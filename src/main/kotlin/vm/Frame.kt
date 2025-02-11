package vm

data class Frame(
    val addr: Int,
    val subs: Stack<Record>,
    val vars: MutableMap<Int, Record>,
    val parent: Frame?,
) {
    private fun lookup(index: Int): Frame? {
        var frame: Frame? = this
        while (frame != null) {
            if (frame.vars.containsKey(index)) {
                return frame
            }
            frame = frame.parent
        }
        return null
    }

    fun get(index: Int): Record {
        val scope = lookup(index)
        return scope?.vars?.get(index) ?: throw IllegalArgumentException("Undefined variable $index")
    }

    fun set(index: Int, value: Record): Record {
        val frame = lookup(index) ?: throw IllegalArgumentException("Undefined variable $index")
        frame.vars[index] = value
        return value
    }

    fun def(index: Int, value: Record): Record {
        if (vars.containsKey(index)) throw IllegalArgumentException("Variable $index is already defined")
        vars[index] = value
        return value
    }
}
