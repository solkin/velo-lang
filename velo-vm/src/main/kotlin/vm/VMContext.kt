package vm

import core.BoundNative
import core.DataClassInfo
import core.NativeRegistry

import vm.actors.ActorHandle
import vm.actors.ActorResponse
import vm.actors.ActorRuntime
import vm.records.RefRecord
import vm.actors.Promise

/**
 * Context for VM execution.
 * Provides access to all VM subsystems for operations.
 *
 * `actorRuntime` is shared across the whole program (main thread + all actor
 * threads) and is used by `ActorSpawn` to create new actors.
 *
 * `currentActor` is set only when this context belongs to an [ActorHandle]'s
 * worker thread. Operations that need to know "am I executing inside an actor"
 * (e.g. for actor-aware identity assignment) consult this field.
 */
class VMContext(
    val stack: Stack<Frame>,
    val frameLoader: FrameLoader,
    val memory: MemoryArea,
    val nativeRegistry: NativeRegistry,
    val actorRuntime: ActorRuntime = ActorRuntime(),
    val currentActor: ActorHandle? = null,
    val natives: Array<BoundNative> = emptyArray(),
    /** Marshalling metadata for `data class` values, keyed by class frame number. */
    val dataClasses: Map<Int, DataClassInfo> = emptyMap(),
    /** Per-class method tables (name -> instance slot), keyed by class frame number, for interface dispatch. */
    val methodTables: Map<Int, Map<String, Int>> = emptyMap(),
) {
    /**
     * Get current frame from stack
     */
    fun currentFrame(): Frame = stack.peek()

    /**
     * Push a new frame onto the stack
     */
    fun pushFrame(frame: Frame) = stack.push(frame)

    /**
     * Pop frame from stack
     */
    fun popFrame(): Frame = stack.pop()

    /**
     * Check if stack is empty
     */
    fun isStackEmpty(): Boolean = stack.empty()

    // ---- Suspension support (VEL-11) ----
    //
    // The Velo call stack is fully reified here (a [Stack] of [Frame]s) rather
    // than living on the JVM stack, so a computation can be parked simply by
    // lifting its frames off and restoring them later — "coroutines without
    // threads". [suspensionEnabled] gates whether a pending `await` yields the
    // fiber (top-level dispatcher tasks) or blocks the thread (nested/inline
    // calls, whose JVM frames cannot be parked). Only [VMExecutor], the
    // [Interpreter] and the actor fiber driver touch these.

    /** When true, an `await` on a not-yet-ready future suspends instead of blocking. */
    var suspensionEnabled: Boolean = false

    private var pendingSuspend: Promise<ActorResponse>? = null

    /** Mark that the current op wants to park on [future]; observed by [VMExecutor]. */
    fun requestSuspend(future: Promise<ActorResponse>) {
        pendingSuspend = future
    }

    /** Whether the executor loop should yield after the current op. */
    fun hasPendingSuspend(): Boolean = pendingSuspend != null

    /** Take and clear the future the fiber is parking on. */
    fun takePendingSuspend(): Promise<ActorResponse>? =
        pendingSuspend.also { pendingSuspend = null }

    /**
     * Lift the entire call stack off for a suspended fiber, returned top-first.
     * Between dispatcher tasks the stack is always empty, so a parked fiber owns
     * the whole stack and a later [restoreStack] cannot collide with other tasks.
     */
    fun detachStack(): List<Frame> {
        val saved = ArrayList<Frame>()
        while (!stack.empty()) saved.add(stack.pop())
        return saved
    }

    /** Restore frames captured by [detachStack], re-establishing their order. */
    fun restoreStack(saved: List<Frame>) {
        for (i in saved.indices.reversed()) stack.push(saved[i])
    }

    /**
     * Load a frame by number.
     *
     * @param parentVars the variable chain to attach as the new frame's
     *   parent for closure / lexical-scope lookup.
     */
    fun loadFrame(num: Int, parentVars: Vars?): Frame? = frameLoader.loadFrame(num, parentVars)

    /**
     * Store a value in the memory area and return its ID
     */
    fun memoryPut(value: Any): Int = memory.put(value)

    /**
     * Get a value from the memory area by ID
     */
    fun <T> memoryGet(id: Int): T = memory.get(id)

    /**
     * Release a value from the memory area
     */
    fun memoryRelease(id: Int) = memory.release(id)

    /**
     * Run a mark-sweep collection if the heap has grown past its threshold.
     * Called by [VMExecutor] at op boundaries, where the operand stacks hold
     * every live value so the root set is precise. Roots are this context's
     * call stack plus, for an actor context, its parked fibers and root
     * objects ([ActorHandle.gcRoots]).
     */
    fun collectIfNeeded() {
        if (!memory.shouldCollect()) return
        HeapCollector.collect(this, currentActor?.gcRoots() ?: emptyList())
    }
}
