package vm

import vm.records.EmptyRecord

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
        return scope?.vars?.get(index) ?: throw IllegalArgumentException("Undefined variable $index on var get")
    }

    fun set(index: Int, value: Record): Record {
        val scope = lookup(index) ?: throw IllegalArgumentException("Undefined variable $index on var set")
        scope.vars[index] = value
        return value
    }

    fun empty(): Boolean = vars.isEmpty()

}

fun createVars(vars: List<Int>, parent: Vars? = null) = Vars(
    vars = HashMap<Int, Record>().apply {
        putAll(vars.map { it to EmptyRecord })
    },
    parent = parent
)
