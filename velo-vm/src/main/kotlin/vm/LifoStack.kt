package vm

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

    override fun empty(): Boolean {
        return deque.isEmpty()
    }

    override fun size(): Int {
        return deque.size
    }

    override fun forEach(action: (T) -> Unit) {
        deque.forEach(action)
    }

}
