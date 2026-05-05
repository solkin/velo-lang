package vm

import vm.actors.ActorHandle
import vm.actors.ActorRuntime
import vm.records.RefRecord

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
}
