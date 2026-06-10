package vm.actors


import vm.Frame
import vm.FrameLoader
import vm.LifoStack
import vm.MemoryAreaImpl
import core.NativeRegistry
import vm.Record
import vm.VMContext
import vm.VMExecutor
import vm.VMProfiler
import vm.createVars
import vm.records.EmptyRecord
import vm.records.FuncRecord
import vm.records.RefKind
import vm.records.RefRecord
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * One live execution domain: an isolated [VMContext] plus a [Dispatcher]
 * that runs its requests serially.
 *
 * Both spawned actors and the program's main context are `ActorHandle`s —
 * "everything is an actor". The only difference is the dispatcher:
 * spawned actors get a dedicated [ThreadDispatcher]; the main context runs
 * on a [PumpDispatcher] (CLI) or a host-provided serial executor (embedded,
 * e.g. an Android main-looper Handler). That uniformity is what lets a
 * Velo callback always execute on the thread that owns its closure.
 *
 * Lifetime:
 *   - Dedicated dispatcher threads are JVM daemons — actor code never
 *     blocks JVM exit.
 *   - GC-driven shutdown: every [ActorRefRecord] / [FutureRecord] increments
 *     [refCount] in its constructor and decrements it from a
 *     [java.lang.ref.Cleaner] action when the record is collected. When the
 *     count drops to zero the dispatcher is asked to drain and stop.
 *   - On program exit [vm.VM.run]'s `finally` calls
 *     [ActorRuntime.shutdownAll] for deterministic teardown.
 *
 * Thread affinity:
 *   - Every [Frame] living in this handle's [VMContext] is owned by the
 *     dispatcher. Calls coming from outside are serialised through
 *     [post] and executed as dispatcher tasks.
 *   - Object identity for outgoing `actor[T]` refs is preserved via
 *     [frameToId] / [idToFrame], so two `await ref.method()` calls that
 *     internally yield the same Velo instance hand back the same `objectId`.
 */
class ActorHandle private constructor(
    val id: Int,
    private val name: String,
    private val runtime: ActorRuntime,
    sharedFrameLoader: FrameLoader,
    sharedNativeRegistry: NativeRegistry,
    sharedNatives: Array<core.BoundNative>,
    private val dispatcher: Dispatcher,
    profiler: VMProfiler? = null,
) {

    /** Live `actor[T]`/`future[T]`/callback reference count — see class kdoc. */
    val refCount = AtomicInteger(0)

    private val nextObjectId = AtomicInteger(0)
    private val idToFrame = HashMap<Int, Frame>()
    private val frameToId = IdentityHashMap<Frame, Int>()

    internal val ctx: VMContext = VMContext(
        stack = LifoStack(),
        frameLoader = sharedFrameLoader,
        memory = MemoryAreaImpl(),
        nativeRegistry = sharedNativeRegistry,
        actorRuntime = runtime,
        currentActor = this,
        natives = sharedNatives,
    )

    private val executor = VMExecutor(ctx, profiler)

    @Volatile
    private var shuttingDown = false

    /** The thread currently executing one of this handle's tasks, if any. */
    @Volatile
    private var activeThread: Thread? = null

    /**
     * True when the calling thread is right now executing this handle's
     * task — i.e. it is safe to touch this handle's frames directly instead
     * of going through the mailbox. Used for inline callback invocation.
     */
    fun isOnOwnThread(): Boolean = Thread.currentThread() === activeThread

    /**
     * Enqueue a request for serial execution on this handle's dispatcher.
     * After shutdown the request is failed fast (futures complete with
     * [ActorResponse.Failure]) instead of hanging in a dead queue.
     */
    private fun post(req: ActorRequest) {
        if (shuttingDown) {
            failDead(req)
            return
        }
        dispatcher.execute {
            activeThread = Thread.currentThread()
            try {
                dispatch(req)
            } finally {
                activeThread = null
            }
        }
    }

    private fun dispatch(req: ActorRequest) {
        when (req) {
            is ActorRequest.Main -> handleMain(req)
            is ActorRequest.Construct -> handleConstruct(req)
            is ActorRequest.Call -> handleCall(req)
            is ActorRequest.InvokeFunc -> handleInvoke(req)
            ActorRequest.Shutdown -> handleShutdown()
        }
    }

    private fun failDead(req: ActorRequest) {
        val message = "[actor $name] is shut down"
        when (req) {
            is ActorRequest.Construct -> req.response.complete(ActorResponse.Failure(message))
            is ActorRequest.Call -> req.response.complete(ActorResponse.Failure(message))
            is ActorRequest.InvokeFunc -> req.response?.complete(ActorResponse.Failure(message))
            is ActorRequest.Main, ActorRequest.Shutdown -> Unit
        }
    }

    /**
     * Run a whole program frame. Failures are routed to the request's
     * `onFailure` when provided (embedded hosts), otherwise they propagate
     * into the dispatcher — for [PumpDispatcher] that surfaces them on the
     * thread blocked in [vm.VM.run], exactly like the pre-actor main loop.
     */
    private fun handleMain(req: ActorRequest.Main) {
        try {
            val frame = ctx.loadFrame(num = req.frameNum, parentVars = null)
                ?: throw IllegalStateException("No main frame")
            ctx.pushFrame(frame)
            executor.run()
        } catch (ex: Throwable) {
            val onFailure = req.onFailure ?: throw ex
            unwindStack()
            onFailure(ex)
        }
    }

    private fun handleShutdown() {
        shuttingDown = true
        runtime.unregister(id)
        dispatcher.close()
    }

    private fun handleConstruct(req: ActorRequest.Construct) = respond(req.response) {
        val classFrame = ctx.loadFrame(req.classFrameNum, parentVars = null)
            ?: error("Class frame ${req.classFrameNum} not found")
        val produced = runFrame(classFrame, req.args)
        val ref = produced as? RefRecord
            ?: error("Actor constructor must produce a class instance, got ${produced::class.simpleName}")
        require(ref.kind == RefKind.CLASS) {
            "Actor constructor must produce a class instance, got ${ref.kind}"
        }
        ActorResponse.Constructed(registerObject(ref.get(ctx)))
    }

    /**
     * Run a function value owned by this actor — callback delivery.
     *
     * With a response future (host-side `VeloFunction.call`) failures follow
     * the normal respond protocol. Fire-and-forget posts have nobody to
     * report to, so failures are program-fatal: Velo has no exception
     * handling, every runtime error stops the program loudly, and a silently
     * swallowed callback error would be the one inconsistent exception to
     * that rule. [vm.HaltException] passes through the same channel and is
     * recognised by the top-level handlers as a user-requested stop.
     */
    private fun handleInvoke(req: ActorRequest.InvokeFunc) {
        val response = req.response
        if (response != null) {
            respond(response) {
                ActorResponse.Returned(invokeFunc(req.func, req.args))
            }
        } else {
            try {
                invokeFunc(req.func, req.args)
            } catch (ex: Throwable) {
                unwindStack()
                runtime.raiseFatal(ex)
            }
        }
    }

    private fun invokeFunc(func: FuncRecord, args: List<ActorValue>): ActorValue {
        val target = ctx.loadFrame(num = func.frameNum, parentVars = func.capturedVars)
            ?: error("Callback frame ${func.frameNum} not found")
        val returned = runFrame(target, args)
        return StructuredClone.encode(returned, ctx)
    }

    private fun handleCall(req: ActorRequest.Call) = respond(req.response) {
        val instance = idToFrame[req.objectId]
            ?: error("Actor object #${req.objectId} no longer alive")
        val callable = instance.vars.get(req.methodVarIndex)
        val funcRec = callable as? FuncRecord
            ?: error("Actor method index ${req.methodVarIndex} is not callable: $callable")
        val methodFrame = ctx.loadFrame(funcRec.frameNum, parentVars = instance.vars)
            ?: error("Method frame ${funcRec.frameNum} not found")
        val returned = runFrame(methodFrame, req.args)
        ActorResponse.Returned(StructuredClone.encode(returned, ctx))
    }

    /**
     * Run [build] and complete [future] with its result, translating any
     * thrown exception into [ActorResponse.Failure] after fully unwinding the
     * actor's call stack. Centralises the error-handling protocol so
     * individual handlers can read top-down without try/catch noise.
     */
    private inline fun respond(
        future: CompletableFuture<ActorResponse>,
        build: () -> ActorResponse,
    ) {
        try {
            future.complete(build())
        } catch (ex: Throwable) {
            unwindStack()
            future.complete(ActorResponse.Failure(ex.message ?: ex.toString()))
        }
    }

    /**
     * Run one Velo frame to completion on this handle's dispatcher thread.
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
     * Schedule the program's main frame. Only used on the main handle.
     */
    fun requestMain(frameNum: Int = 0, onFailure: ((Throwable) -> Unit)? = null) {
        post(ActorRequest.Main(frameNum, onFailure))
    }

    /**
     * Asynchronously invoke a method on an object owned by this actor.
     *
     * Posts the work to the dispatcher and returns the still-pending
     * [CompletableFuture] without blocking. The caller is expected to wrap
     * the future in a [FutureRecord] (which transfers lifetime ownership to
     * the GC-driven counter on this handle) and feed it into a later
     * `await`. Failures surface when the future is awaited, not here.
     */
    fun requestCallAsync(
        objectId: Int,
        methodVarIndex: Int,
        args: List<ActorValue>,
    ): CompletableFuture<ActorResponse> {
        val future = CompletableFuture<ActorResponse>()
        post(ActorRequest.Call(objectId, methodVarIndex, args, future))
        return future
    }

    /**
     * Fire-and-forget delivery of a callback owned by this actor. Used by
     * the `Call` opcode when Velo code invokes a foreign [CallbackRecord]
     * and by [VeloFunction.post]. Failures are program-fatal — see
     * [handleInvoke].
     */
    fun postInvoke(func: FuncRecord, args: List<ActorValue>) {
        post(ActorRequest.InvokeFunc(func, args, response = null))
    }

    /**
     * Invoke a callback owned by this actor and observe its completion.
     * Used by [VeloFunction.call]; failures complete the future with
     * [ActorResponse.Failure] instead of being fatal.
     */
    fun requestInvokeAsync(func: FuncRecord, args: List<ActorValue>): CompletableFuture<ActorResponse> {
        val future = CompletableFuture<ActorResponse>()
        post(ActorRequest.InvokeFunc(func, args, future))
        return future
    }

    /**
     * Synchronously run a callback owned by this actor from a caller that is
     * already executing on this actor's dispatcher (see [isOnOwnThread]) —
     * e.g. a native method invoked from Velo code that calls
     * `VeloFunction.call` before returning. Going through the mailbox would
     * deadlock: the dispatcher cannot pick up the next task while the
     * current one is blocked waiting for it.
     */
    fun invokeInline(func: FuncRecord, args: List<ActorValue>): ActorValue {
        check(isOnOwnThread()) { "invokeInline called from a foreign thread" }
        return invokeFunc(func, args)
    }

    /**
     * Decode an [ActorResponse] produced by this actor into a host-level
     * value, throwing on failures. Used by `FutureAwait` after joining a
     * [FutureRecord]'s future, and by the synchronous test helpers.
     */
    fun unwrapResponse(resp: ActorResponse): ActorValue = when (resp) {
        is ActorResponse.Returned -> resp.value
        is ActorResponse.Failure -> throw RuntimeException("[actor $name] ${resp.message}")
        is ActorResponse.Constructed -> error("Unexpected Constructed response for Call request")
    }

    /** Synchronous shortcut: post + join + unwrap. Kept for tests; production
     *  code goes through `requestCallAsync` + a future-aware opcode. */
    fun requestCall(objectId: Int, methodVarIndex: Int, args: List<ActorValue>): ActorValue {
        return unwrapResponse(requestCallAsync(objectId, methodVarIndex, args).join())
    }

    fun requestShutdown() {
        if (shuttingDown) return
        post(ActorRequest.Shutdown)
    }

    /**
     * Decrement the live-ref counter; if it hits zero, ask the dispatcher to
     * drain and stop. Called by the [java.lang.ref.Cleaner] action attached
     * to each [ActorRefRecord] / [FutureRecord].
     */
    fun releaseRef() {
        if (refCount.decrementAndGet() <= 0) {
            requestShutdown()
        }
    }

    /**
     * Wait at most [timeoutMs] for the dispatcher to finish. Used by tests
     * to assert clean shutdown; production code relies on daemon-thread
     * semantics and doesn't need to join.
     */
    fun joinFor(timeoutMs: Long) {
        dispatcher.joinFor(timeoutMs)
    }

    fun isAlive(): Boolean = dispatcher.isAlive()

    companion object {
        /**
         * Create the handle for the program's main context — "actor #0".
         * No root object is constructed; the program frame is scheduled
         * separately via [requestMain].
         */
        fun main(
            runtime: ActorRuntime,
            sharedFrameLoader: FrameLoader,
            sharedNativeRegistry: NativeRegistry,
            sharedNatives: Array<core.BoundNative> = emptyArray(),
            dispatcher: Dispatcher,
            profiler: VMProfiler? = null,
        ): ActorHandle {
            val handle = ActorHandle(
                id = runtime.nextActorId(),
                name = "main",
                runtime = runtime,
                sharedFrameLoader = sharedFrameLoader,
                sharedNativeRegistry = sharedNativeRegistry,
                sharedNatives = sharedNatives,
                dispatcher = dispatcher,
                profiler = profiler,
            )
            runtime.register(handle)
            return handle
        }

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
            sharedNatives: Array<core.BoundNative>,
            classFrameNum: Int,
            className: String,
            args: List<ActorValue>,
        ): Pair<ActorHandle, Int> {
            val id = runtime.nextActorId()
            val handle = ActorHandle(
                id = id,
                name = className,
                runtime = runtime,
                sharedFrameLoader = sharedFrameLoader,
                sharedNativeRegistry = sharedNativeRegistry,
                sharedNatives = sharedNatives,
                dispatcher = ThreadDispatcher(name = "velo-actor-$id-$className"),
            )
            runtime.register(handle)

            val future = CompletableFuture<ActorResponse>()
            handle.post(ActorRequest.Construct(classFrameNum, args, future))
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
