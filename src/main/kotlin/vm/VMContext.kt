package vm

import vm.records.RefRecord

/**
 * Context for VM execution.
 * Provides access to all VM subsystems for operations.
 */
class VMContext(
    val stack: Stack<Frame>,
    val frameLoader: FrameLoader,
    val memory: MemoryArea,
    val nativeRegistry: NativeRegistry,
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
     * Load a frame by number
     */
    fun loadFrame(num: Int, parent: Frame?): Frame? = frameLoader.loadFrame(num, parent)

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
