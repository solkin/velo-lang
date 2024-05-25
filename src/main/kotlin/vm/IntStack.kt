package vm

class IntStack(
    val array: IntArray,
    val offset: Int,
    val size: Int,
) {

    private var p: Int = 0

    init {
        reset()
    }

    fun reset() {
        this.p = offset + size
    }

    fun pick(n: Int): Int {
        if (p + n >= offset + size) {
            throw IllegalArgumentException("stack out of range")

        }
        return array[p + n]
    }

    fun push(v: Int) {
        if (p - 1 < offset) {
            throw IllegalArgumentException("stack overflow")
        }
        p--
        array[p] = v
    }

    fun peek(): Int {
        return array[p]
    }

    fun pop(): Int {
        if (p >= offset + size) {
            throw IllegalArgumentException("stack underflow")
        }
        val i = array[p]
        p++
        return i
    }

}