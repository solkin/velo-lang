package vm

data class Vars(
    val vars: MutableMap<Int, Record>,
    val parent: Vars?,
) {
    private fun lookup(index: Int): Vars? {
        var vars: Vars? = this
        while (vars != null) {
            if (vars.vars.contains(index)) {
                return vars
            }
            vars = vars.parent
        }
        return null
    }

    fun get(index: Int): Record {
        val scope = lookup(index)
        return scope?.vars?.get(index) ?: throw IllegalArgumentException("Undefined variable $index")
    }

    fun set(index: Int, value: Record): Record {
        val scope = lookup(index) ?: throw IllegalArgumentException("Undefined variable $index")
        scope.vars[index] = value
        return value
    }

    fun def(index: Int, value: Record): Record {
        if (vars.contains(index)) throw IllegalArgumentException("Variable $index is already defined")
        vars[index] = value
        return value
    }

    fun empty(): Boolean = vars.isEmpty()

}

fun createVars(parent: Vars? = null) = Vars(
    vars = HashMap(),
    parent = parent
)
