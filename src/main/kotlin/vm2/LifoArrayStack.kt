package vm2

class LifoArrayStack<T>(
    val array: Array<T?>,
    val offset: Int = 0,
    val size: Int = 2048,
): Stack<T> {

    private var p: Int = 0

    init {
        reset()
    }

    fun reset() {
        this.p = offset + size
    }

    fun pick(n: Int): T {
        if (p + n >= offset + size) {
            throw IllegalArgumentException("stack out of range")

        }
        return array[p + n]!!
    }

    override fun push(value: T) {
        if (p - 1 < offset) {
            throw IllegalArgumentException("stack overflow")
        }
        p--
        array[p] = value
    }

    override fun peek(): T {
        return array[p]!!
    }

    override fun pop(): T {
        if (p >= offset + size) {
            throw IllegalArgumentException("stack underflow")
        }
        val i = array[p]
        p++
        return i!!
    }

}