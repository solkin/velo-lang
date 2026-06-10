package vm

interface Stack<T> {
    fun push(value: T)
    fun peek(): T
    fun pop(): T
    fun empty(): Boolean
}
