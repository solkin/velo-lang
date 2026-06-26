package vm.actors


import vm.Frame
import vm.FrameLoader
import vm.LifoStack
import vm.MemoryAreaImpl
import core.NativeRegistry
import vm.Record
import vm.RunResult
import vm.VMContext
import vm.VMExecutor
import vm.VMProfiler
import vm.createVars
import vm.records.EmptyRecord
import vm.records.FuncRecord
import vm.records.RefKind
import vm.records.RefRecord
import java.util.IdentityHashMap
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
 * Lifetime: explicit, not GC-finalization. An actor lives until shut down by
 * [ActorRuntime.shutdownAll] (called from [vm.VM.run]'s `finally`, or from
 * [vm.VeloProgram.stop]) or by an application-level `close()` it exposes.
 * Velo-internal handles (`actor[T]`/`future[T]`/callbacks between actors) pin
 * nothing — in the cooperative model a dropped-but-unclosed actor holds only
 * memory, reclaimed at exit, not a thread.
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
    sharedDataClasses: Map<Int, core.DataClassInfo>,
    private val dispatcher: Dispatcher,
    profiler: VMProfiler? = null,
) {

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
        dataClasses = sharedDataClasses,
    )

    private val executor = VMExecutor(ctx, profiler)

    @Volatile
    private var shuttingDown = false

    /** The thread currently executing one of this handle's tasks, if any. */
    @Volatile
    private var activeThread: Thread? = null

    /**
     * Fibers parked on an `await` (VEL-11), waiting for their awaited future.
     * Touched only on this handle's dispatcher thread (the [drive] / resume /
     * shutdown paths all run there), so a plain set is safe. Keeps the CLI
     * pump from going idle while a suspended computation is still outstanding.
     */
    private val parkedFibers = HashSet<Fiber>()

    /**
     * A suspended computation: its sentinel base frame (the stack marker used
     * to detect completion), the response to fail on shutdown, and how to
     * deliver its result / route its failure once it finishes.
     */
    private class Fiber(
        val sentinel: Frame,
        val response: Promise<ActorResponse>?,
        val deliver: (Record) -> Unit,
        val fail: (Throwable) -> Unit,
    ) {
        /** This fiber's call stack while parked (detached from the context). */
        var saved: List<Frame> = emptyList()
    }

    /** Whether any fiber is parked — keeps the CLI pump alive across an await. */
    fun hasParkedFibers(): Boolean = parkedFibers.isNotEmpty()

    /**
     * Extra GC roots beyond the live call stack: the frames of fibers parked on
     * an `await` (detached from [ctx]) and the actor's registered root objects,
     * which hold state between messages. Read only on this handle's dispatcher
     * thread — the same thread the collector runs on.
     */
    internal fun gcRoots(): List<Frame> {
        if (parkedFibers.isEmpty() && idToFrame.isEmpty()) return emptyList()
        val roots = ArrayList<Frame>()
        for (fiber in parkedFibers) roots.addAll(fiber.saved)
        roots.addAll(idToFrame.values)
        return roots
    }

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
            is ActorRequest.Call -> handleCall(req)
            is ActorRequest.InvokeFunc -> handleInvoke(req)
            is ActorRequest.Resume -> req.run()
            ActorRequest.Shutdown -> handleShutdown()
        }
    }

    private fun failDead(req: ActorRequest) {
        val message = "[actor $name] is shut down"
        when (req) {
            is ActorRequest.Call -> req.response.resolve(ActorResponse.Failure(message))
            is ActorRequest.InvokeFunc -> req.response?.resolve(ActorResponse.Failure(message))
            is ActorRequest.Resume -> Unit // parked fibers are failed by handleShutdown
            is ActorRequest.Main, ActorRequest.Shutdown -> Unit
        }
    }

    /**
     * Run the program's whole main frame as a fiber. Failures route to the
     * request's `onFailure` when provided (embedded hosts), otherwise they
     * propagate into the dispatcher — for [PumpDispatcher] that surfaces them on
     * the thread blocked in [vm.VM.run], exactly like the pre-actor main loop,
     * with the call stack left intact for the error dump.
     */
    private fun handleMain(req: ActorRequest.Main) = runFiber(
        args = emptyList(),
        response = null,
        prepareTarget = {
            ctx.loadFrame(num = req.frameNum, parentVars = null)
                ?: throw IllegalStateException("No main frame")
        },
        deliver = { /* the main frame finished; nothing to hand back */ },
        fail = { ex ->
            val onFailure = req.onFailure ?: throw ex
            unwindStack()
            onFailure(ex)
        },
    )

    private fun handleShutdown() {
        shuttingDown = true
        // Fail any fiber still parked on an await so its awaiter doesn't hang.
        for (fiber in parkedFibers) {
            fiber.response?.resolve(ActorResponse.Failure("[actor $name] is shut down"))
        }
        parkedFibers.clear()
        runtime.unregister(id)
        dispatcher.close()
    }

    /**
     * Synchronously construct this actor's root object on the *calling* thread,
     * returning its objectId. Construction is inline (no mailbox round-trip) so
     * `spawn` never posts to a dispatcher and then waits on it — which would
     * deadlock when all actors share one cooperative loop (the loop can't run
     * the construct task while the spawner is blocked on its result).
     * Suspension is off, so an `await` in a constructor blocks (accepted: a
     * spawn is synchronous by contract). The constructed state is published to
     * the actor's later turns through the happens-before of its dispatcher.
     */
    private fun constructInline(classFrameNum: Int, args: List<ActorValue>): Int {
        val target = ctx.loadFrame(classFrameNum, parentVars = null)
            ?: error("Class frame $classFrameNum not found")
        val previous = activeThread
        activeThread = Thread.currentThread()
        try {
            val produced = runFrameBlocking(target, args)
            val ref = produced as? RefRecord
                ?: error("Actor constructor must produce a class instance, got ${produced::class.simpleName}")
            require(ref.kind == RefKind.CLASS) {
                "Actor constructor must produce a class instance, got ${ref.kind}"
            }
            return registerObject(ref.get(ctx))
        } finally {
            activeThread = previous
        }
    }

    /**
     * Run a function value owned by this actor — callback delivery — as a fiber.
     *
     * With a response future (host-side `VeloFunction.call`) failures follow the
     * normal respond protocol. Fire-and-forget posts have nobody to report to,
     * so failures are program-fatal via [ActorRuntime.raiseFatal]: Velo has no
     * exception handling, every runtime error stops the program loudly, and a
     * silently swallowed callback error would be the one inconsistent exception
     * to that rule. [vm.HaltException] flows through the same channel and is
     * recognised by the top-level handlers as a user-requested stop.
     */
    private fun handleInvoke(req: ActorRequest.InvokeFunc) {
        val response = req.response
        runFiber(
            args = req.args,
            response = response,
            prepareTarget = {
                ctx.loadFrame(num = req.func.frameNum, parentVars = req.func.capturedVars)
                    ?: error("Callback frame ${req.func.frameNum} not found")
            },
            deliver = { result ->
                response?.resolve(ActorResponse.Returned(StructuredClone.encode(result, ctx)))
            },
            fail = { ex ->
                unwindStack()
                if (response != null) response.resolve(ActorResponse.Failure(ex.message ?: ex.toString()))
                else runtime.raiseFatal(ex)
            },
        )
    }

    private fun handleCall(req: ActorRequest.Call) = runFiber(
        args = req.args,
        response = req.response,
        prepareTarget = {
            val instance = idToFrame[req.objectId]
                ?: error("Actor object #${req.objectId} no longer alive")
            val callable = instance.vars.get(req.methodVarIndex)
            val funcRec = callable as? FuncRecord
                ?: error("Actor method index ${req.methodVarIndex} is not callable: $callable")
            ctx.loadFrame(funcRec.frameNum, parentVars = instance.vars)
                ?: error("Method frame ${funcRec.frameNum} not found")
        },
        deliver = { req.response.resolve(ActorResponse.Returned(StructuredClone.encode(it, ctx))) },
        fail = { unwindStack(); req.response.resolve(ActorResponse.Failure(it.message ?: it.toString())) },
    )

    /**
     * Synchronously run a function value owned by this actor to completion —
     * the inline-invocation path ([invokeInline]), where a native called from
     * Velo code invokes its callback before returning. Inline calls run nested
     * inside an op on the JVM stack, which cannot be parked, so an `await`
     * inside blocks rather than suspends (see [runFrameBlocking]).
     */
    private fun invokeFunc(func: FuncRecord, args: List<ActorValue>): ActorValue {
        val target = ctx.loadFrame(num = func.frameNum, parentVars = func.capturedVars)
            ?: error("Callback frame ${func.frameNum} not found")
        val returned = runFrameBlocking(target, args)
        return StructuredClone.encode(returned, ctx)
    }

    /**
     * Set up a top-level dispatcher task as a fiber and drive it to completion,
     * possibly across one or more `await` suspensions (VEL-11).
     *
     * The sentinel trick still applies: a zero-op frame is pushed underneath the
     * target as a "stop here" marker for [VMExecutor] — when the call stack
     * walks back to it the executor returns and the sentinel's operand stack
     * holds the target frame's result. Arguments are decoded into this context's
     * memory and pushed onto the target in declaration order before execution.
     * [prepareTarget] resolves the frame to run; throwing from it (or from
     * [deliver]) routes through [fail].
     */
    private fun runFiber(
        args: List<ActorValue>,
        response: Promise<ActorResponse>?,
        prepareTarget: () -> Frame,
        deliver: (Record) -> Unit,
        fail: (Throwable) -> Unit,
    ) {
        ctx.suspensionEnabled = true
        val sentinel = sentinelFrame()
        val fiber = Fiber(sentinel, response, deliver, fail)
        try {
            val target = prepareTarget()
            val decoded = args.map { StructuredClone.decode(it, ctx) }
            ctx.pushFrame(sentinel)
            decoded.forEach { target.subs.push(it) }
            ctx.pushFrame(target)
        } catch (ex: Throwable) {
            unwindStack()
            fail(ex)
            return
        }
        drive(fiber)
    }

    /**
     * Advance a fiber until it either finishes (deliver its result) or parks on
     * an `await` (lift its frames off and arrange a resume). Always runs on this
     * handle's dispatcher thread.
     */
    private fun drive(fiber: Fiber) {
        try {
            when (executor.run()) {
                RunResult.SUSPENDED -> {
                    val awaited = ctx.takePendingSuspend()
                        ?: error("Fiber suspended without a pending future")
                    fiber.saved = ctx.detachStack()
                    parkedFibers.add(fiber)
                    awaited.onComplete { resume(fiber) }
                }
                RunResult.COMPLETED -> {
                    // The target may end in `Ret` (sentinel now on top, result
                    // on its operand stack) or simply run off the end without
                    // returning — the program (main) frame does the latter,
                    // leaving itself above the sentinel. Drain any such finished
                    // frame down to our marker; the sentinel's operand stack
                    // holds the result for Ret-terminated targets, EmptyRecord
                    // otherwise (main's result is discarded anyway).
                    var tail = ctx.popFrame()
                    while (tail !== fiber.sentinel && !ctx.isStackEmpty()) tail = ctx.popFrame()
                    check(tail === fiber.sentinel) { "Actor fiber execution: stack out of sync" }
                    fiber.deliver(if (tail.subs.empty()) EmptyRecord else tail.subs.pop())
                }
            }
        } catch (ex: Throwable) {
            fiber.fail(ex)
        }
    }

    /**
     * Re-enter a parked fiber on this handle's own dispatcher once its awaited
     * future completes. Runs on the completing thread, so it only enqueues — the
     * restore + interpretation happens as a dispatcher task, preserving strict
     * serial execution. The parked `FutureAwait` op then re-runs and, with the
     * future now done, takes the fast (non-suspending) path.
     */
    private fun resume(fiber: Fiber) {
        try {
            post(ActorRequest.Resume {
                if (!shuttingDown) {
                    parkedFibers.remove(fiber)
                    ctx.restoreStack(fiber.saved)
                    fiber.saved = emptyList()
                    drive(fiber)
                }
            })
        } catch (ex: Throwable) {
            // The dispatcher rejected the resume — e.g. an embedded host tore
            // down its executor without going through VeloProgram.stop(). Fail
            // the awaiter rather than leave it hanging. Completing the response
            // is thread-safe; we must not touch ctx from this (foreign,
            // future-completing) thread.
            fiber.response?.resolve(ActorResponse.Failure("[actor $name] could not resume: ${ex.message ?: ex}"))
        }
    }

    /**
     * Run one Velo frame to completion synchronously — the nested-invocation
     * path. Suspension is disabled for the duration: a nested call lives on the
     * JVM stack and cannot be parked, so an `await` inside blocks (as it did
     * before VEL-11). Same sentinel mechanism as [runFiber].
     */
    private fun runFrameBlocking(target: Frame, args: List<ActorValue>): Record {
        val previousSuspension = ctx.suspensionEnabled
        ctx.suspensionEnabled = false
        try {
            val decoded = args.map { StructuredClone.decode(it, ctx) }
            val sentinel = sentinelFrame()
            ctx.pushFrame(sentinel)
            decoded.forEach { target.subs.push(it) }
            ctx.pushFrame(target)
            executor.run()
            val tail = ctx.popFrame()
            check(tail === sentinel) { "Actor frame execution: stack out of sync" }
            return if (tail.subs.empty()) EmptyRecord else tail.subs.pop()
        } finally {
            ctx.suspensionEnabled = previousSuspension
        }
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
     * [Promise] without blocking. The caller is expected to wrap
     * the future in a [FutureRecord] (which transfers lifetime ownership to
     * the GC-driven counter on this handle) and feed it into a later
     * `await`. Failures surface when the future is awaited, not here.
     */
    fun requestCallAsync(
        objectId: Int,
        methodVarIndex: Int,
        args: List<ActorValue>,
    ): Promise<ActorResponse> {
        val future = Promise<ActorResponse>()
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
    fun requestInvokeAsync(func: FuncRecord, args: List<ActorValue>): Promise<ActorResponse> {
        val future = Promise<ActorResponse>()
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
    }

    /** Synchronous shortcut: post + join + unwrap. Kept for tests; production
     *  code goes through `requestCallAsync` + a future-aware opcode. */
    fun requestCall(objectId: Int, methodVarIndex: Int, args: List<ActorValue>): ActorValue {
        return unwrapResponse(requestCallAsync(objectId, methodVarIndex, args).await())
    }

    fun requestShutdown() {
        if (shuttingDown) return
        post(ActorRequest.Shutdown)
    }

    /**
     * Host-held-callback liveness, delegated to the shared runtime: a native
     * that retains a [VeloFunctionImpl] to fire later keeps the CLI event loop
     * ([vm.VM.run]) alive until it releases. VeloFunctionImpl tracks its own
     * retained state so retain/release stay balanced.
     */
    internal fun retainHostCallback() = runtime.retainCallback()
    internal fun releaseHostCallback() = runtime.releaseCallback()

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
            sharedDataClasses: Map<Int, core.DataClassInfo> = emptyMap(),
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
                sharedDataClasses = sharedDataClasses,
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
            sharedDataClasses: Map<Int, core.DataClassInfo>,
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
                sharedDataClasses = sharedDataClasses,
                dispatcher = runtime.newActorDispatcher("velo-actor-$id-$className"),
            )
            runtime.register(handle)
            val rootObjectId = try {
                handle.constructInline(classFrameNum, args)
            } catch (ex: Throwable) {
                handle.requestShutdown()
                throw RuntimeException("[actor $className] ${ex.message ?: ex}")
            }
            return handle to rootObjectId
        }
    }
}
