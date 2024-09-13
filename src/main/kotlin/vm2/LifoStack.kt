package vm2

import java.util.ArrayDeque
import java.util.Deque

class LifoStack<T> : Stack<T> {

    private val deque: Deque<T> = ArrayDeque()

    override fun push(value: T) {
        deque.push(value)
    }

    override fun peek(): T {
        return deque.peek()
    }

    override fun pop(): T {
        return deque.pop()
    }

}