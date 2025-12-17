package vm

/**
 * Context for VM execution.
 * Provides access to all VM subsystems for operations.
 */
class VMContext(
    val stack: Stack<Frame>,
    val frameLoader: FrameLoader,
    val heap: Heap,
    val nativeArea: Native,
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
     * Store a value in the heap and return its ID
     */
    fun heapPut(value: Any): Int = heap.put(value)

    /**
     * Get a value from the heap by ID
     */
    fun <T> heapGet(id: Int): T = heap.get(id)

    /**
     * Store a native object and return a NativeRecord
     */
    fun nativePut(value: Any): vm.records.NativeRecord = nativeArea.put(value)

    /**
     * Get a native object by NativeRecord
     */
    fun <T> nativeGet(record: vm.records.NativeRecord): T = nativeArea.get(record)
}

