package vm

import java.util.TreeMap

class Scope<T> internal constructor(
    private val vars: MutableMap<Int, T>,
    val parent: Scope<T>?
) {

    fun extend() = Scope(TreeMap(), this)

    private fun lookup(index: Int): Scope<T>? {
        var scope: Scope<T>? = this
        while (scope != null) {
            if (scope.vars.containsKey(index)) {
                return scope
            }
            scope = scope.parent
        }
        return null
    }

    fun get(index: Int): T {
        val scope = lookup(index)
        return scope?.vars?.get(index) ?: throw IllegalArgumentException("Undefined variable $index")
    }

    fun set(index: Int, value: T): T {
        val scope = lookup(index) ?: throw IllegalArgumentException("Undefined variable $index")
        scope.vars[index] = value
        return value
    }

    fun def(index: Int, value: T): T {
        if (vars.containsKey(index)) throw IllegalArgumentException("Variable $index is already defined")
        vars[index] = value
        return value
    }

}

fun <T> createGlobalScope() = Scope<T>(TreeMap(), null)
