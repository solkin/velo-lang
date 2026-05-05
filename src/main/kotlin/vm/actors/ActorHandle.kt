package vm.actors

import vm.Frame
import vm.FrameLoader
import vm.LifoStack
import vm.MemoryAreaImpl
import vm.NativeRegistry
import vm.Record
import vm.VMContext
import vm.VMExecutor
import vm.createVars
import vm.records.EmptyRecord
import vm.records.FuncRecord
import vm.records.RefKind
import vm.records.RefRecord
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * One live actor: a daemon Kotlin thread, an isolated [VMContext], and a
 * request mailbox.
 *
 * Lifetime:
 *   - The worker thread is a JVM daemon — actor code never blocks JVM exit.
 *   - GC-driven shutdown: every [ActorRefRecord] increments [refCount] in its
 *     constructor and decrements it from a [java.lang.ref.Cleaner] action when
 *     the record is collected. When the count drops to zero the worker is
 *     asked to drain its mailbox and exit.
 *   - On program exit [vm.VM.run]'s `finally` calls
 *     [ActorRuntime.shutdownAll] for deterministic teardown.
 *
 * Thread affinity:
 *   - Every [Frame] living in this actor's [VMContext] is owned by the worker
 *     thread. Calls coming from outside the worker are serialised through the
 *     mailbox and executed on the worker.
 *   - Object identity for outgoing `actor[T]` refs is preserved via
 *     [frameToId] / [idToFrame], so two `await ref.method()` calls that
 *     internally yield the same Velo instance hand back the same `objectId`.
 */
class ActorHandle private constructor(
    val id: Int,
    private val rootClassName: String,
    private val runtime: ActorRuntime,
    sharedFrameLoader: FrameLoader,
    sharedNativeRegistry: NativeRegistry,
) {

    private val mailbox = LinkedBlockingQueue<ActorRequest>()

    /** Live `actor[T]` reference count — see class kdoc. */
    val refCount = AtomicInteger(0)

    private val nextObjectId = AtomicInteger(0)
    private val idToFrame = HashMap<Int, Frame>()
    private val frameToId = IdentityHashMap<Frame, Int>()

    private val ctx: VMContext = VMContext(
        stack = LifoStack(),
        frameLoader = sharedFrameLoader,
        memory = MemoryAreaImpl(),
        nativeRegistry = sharedNativeRegistry,
        actorRuntime = runtime,
        currentActor = this,
    )

    private val executor = VMExecutor(ctx)

    @Volatile private var shuttingDown = false

    private val worker: Thread = Thread({ workerLoop() }, "velo-actor-$id-$rootClassName").apply {
        isDaemon = true
        start()
    }

    private fun workerLoop() {
        try {
            while (true) {
                val req = mailbox.take()
                when (req) {
                    is ActorRequest.Construct -> handleConstruct(req)
                    is ActorRequest.Call -> handleCall(req)
                    ActorRequest.Shutdown -> {
                        shuttingDown = true
                        var pending = mailbox.poll()
                        while (pending != null) {
                            when (pending) {
                                is ActorRequest.Construct -> handleConstruct(pending)
                                is ActorRequest.Call -> handleCall(pending)
                                ActorRequest.Shutdown -> Unit
                            }
                            pending = mailbox.poll()
                        }
                        return
                    }
                }
            }
        } finally {
            runtime.unregister(id)
        }
    }

    private fun handleConstruct(req: ActorRequest.Construct) {
        try {
            val classFrame = ctx.loadFrame(req.classFrameNum, parentVars = null)
                ?: error("Class frame ${req.classFrameNum} not found")
            val produced = runFrame(classFrame, req.args)
            val ref = produced as? RefRecord
                ?: error("Actor constructor must produce a class instance, got ${produced::class.simpleName}")
            require(ref.kind == RefKind.CLASS) {
                "Actor constructor must produce a class instance, got ${ref.kind}"
            }
            val objectId = registerObject(ref.get(ctx))
            req.response.complete(ActorResponse.Constructed(objectId))
        } catch (ex: Throwable) {
            unwindStack()
            req.response.complete(ActorResponse.Failure(ex.message ?: ex.toString()))
        }
    }

    private fun handleCall(req: ActorRequest.Call) {
        try {
            val instance = idToFrame[req.objectId]
                ?: error("Actor object #${req.objectId} no longer alive")
            val callable = instance.vars.get(req.methodVarIndex)
            val funcRec = callable as? FuncRecord
                ?: error("Actor method index ${req.methodVarIndex} is not callable: $callable")
            val methodFrame = ctx.loadFrame(funcRec.frameNum, parentVars = instance.vars)
                ?: error("Method frame ${funcRec.frameNum} not found")
            val returned = runFrame(methodFrame, req.args)
            req.response.complete(ActorResponse.Returned(StructuredClone.encode(returned, ctx)))
        } catch (ex: Throwable) {
            unwindStack()
            req.response.complete(ActorResponse.Failure(ex.message ?: ex.toString()))
        }
    }

    /**
     * Run one Velo frame to completion on this actor's worker thread.
     *
     * A zero-op sentinel frame is pushed underneath as a "stop here" marker
     * for [VMExecutor]: the executor returns the moment the call stack
     * walks back to the sentinel. The sentinel's operand stack therefore
     * holds the topmost value left by the target frame's `Ret`, which we
     * pop and return to the caller (or [EmptyRecord] for void methods).
     *
     * Arguments are decoded into this context's [vm.MemoryArea] and pushed
     * onto the target frame in declaration order before execution starts,
     * matching the convention used by ordinary `Call` opcodes elsewhere.
     */
    private fun runFrame(target: Frame, args: List<ActorValue>): Record {
        val decoded = args.map { StructuredClone.decode(it, ctx) }
        val sentinel = sentinelFrame()
        ctx.pushFrame(sentinel)
        decoded.forEach { target.subs.push(it) }
        ctx.pushFrame(target)
        executor.run()
        val tail = ctx.popFrame()
        check(tail === sentinel) { "Actor frame execution: stack out of sync" }
        return if (tail.subs.empty()) EmptyRecord else tail.subs.pop()
    }

    private fun registerObject(frame: Frame): Int {
        frameToId[frame]?.let { return it }
        val id = nextObjectId.getAndIncrement()
        idToFrame[id] = frame
        frameToId[frame] = id
        return id
    }

    private fun unwindStack() {
        while (!ctx.isStackEmpty()) ctx.popFrame()
    }

    private fun sentinelFrame(): Frame = Frame(
        pc = 0,
        subs = LifoStack(),
        vars = createVars(vars = emptyList(), parent = null),
        ops = emptyList(),
    )

    /**
     * Synchronously invoke a method on an object owned by this actor.
     *
     * Returns the marshalled response — the caller decodes it back into its
     * own [VMContext]. Failures propagate as [RuntimeException]s.
     */
    fun requestCall(objectId: Int, methodVarIndex: Int, args: List<ActorValue>): ActorValue {
        val future = CompletableFuture<ActorResponse>()
        mailbox.put(ActorRequest.Call(objectId, methodVarIndex, args, future))
        return when (val resp = future.join()) {
            is ActorResponse.Returned -> resp.value
            is ActorResponse.Failure -> throw RuntimeException("[actor $rootClassName] ${resp.message}")
            is ActorResponse.Constructed -> error("Unexpected Constructed response for Call request")
        }
    }

    fun requestShutdown() {
        if (shuttingDown) return
        mailbox.put(ActorRequest.Shutdown)
    }

    /**
     * Decrement the live-ref counter; if it hits zero, ask the worker to
     * drain and exit. Called by the [java.lang.ref.Cleaner] action attached
     * to each [ActorRefRecord].
     */
    fun releaseRef() {
        if (refCount.decrementAndGet() <= 0) {
            requestShutdown()
        }
    }

    /**
     * Wait at most [timeoutMs] for the worker thread to finish. Used by tests
     * to assert clean shutdown; production code relies on daemon-thread
     * semantics and doesn't need to join.
     */
    fun joinFor(timeoutMs: Long) {
        worker.join(timeoutMs)
    }

    fun isAlive(): Boolean = worker.isAlive

    companion object {
        /**
         * Spawn a new actor and synchronously construct its root object.
         *
         * Construction is part of `spawn` so the caller never sees a
         * half-built actor: when this returns, the root object's vars are
         * fully initialised and ready to dispatch methods.
         *
         * Returns a pair of (handle, rootObjectId). The caller is expected
         * to wrap them into an [ActorRefRecord] immediately, which transfers
         * lifetime ownership to the GC-driven counter on the handle.
         */
        fun spawn(
            runtime: ActorRuntime,
            sharedFrameLoader: FrameLoader,
            sharedNativeRegistry: NativeRegistry,
            classFrameNum: Int,
            className: String,
            args: List<ActorValue>,
        ): Pair<ActorHandle, Int> {
            val handle = ActorHandle(
                id = runtime.nextActorId(),
                rootClassName = className,
                runtime = runtime,
                sharedFrameLoader = sharedFrameLoader,
                sharedNativeRegistry = sharedNativeRegistry,
            )
            runtime.register(handle)

            val future = CompletableFuture<ActorResponse>()
            handle.mailbox.put(ActorRequest.Construct(classFrameNum, args, future))
            val rootObjectId = when (val resp = future.join()) {
                is ActorResponse.Constructed -> resp.rootObjectId
                is ActorResponse.Failure -> {
                    handle.requestShutdown()
                    throw RuntimeException("[actor $className] ${resp.message}")
                }
                is ActorResponse.Returned -> error("Unexpected Returned response for Construct request")
            }
            return handle to rootObjectId
        }
    }
}
