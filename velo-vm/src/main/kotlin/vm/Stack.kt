package vm

interface Stack<T> {
    fun push(value: T)
    fun peek(): T
    fun pop(): T
    fun empty(): Boolean

    /** Visit every element without modifying the stack (used by the GC to mark roots). */
    fun forEach(action: (T) -> Unit)
}
