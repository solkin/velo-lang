package vm

interface Stack<T> {
    fun push(value: T)
    fun peek(): T
    fun pop(): T
    fun empty(): Boolean

    /** Number of elements — used to truncate an operand stack back to a try handler's mark. */
    fun size(): Int

    /** Visit every element without modifying the stack (used by the GC to mark roots). */
    fun forEach(action: (T) -> Unit)
}
