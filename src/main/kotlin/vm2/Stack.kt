package vm2

interface Stack<T> {

    fun push(value: T)

    fun peek(): T

    fun pop(): T

}