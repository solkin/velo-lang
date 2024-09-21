package compiler

import compiler.nodes.Type
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger

class Scope internal constructor(
    private val vars: MutableMap<String, Var>,
    val parent: Scope?,
    private val counter: AtomicInteger,
) {

    fun extend() = Scope(TreeMap(), parent = this, counter)

    private fun lookup(name: String): Scope? {
        var scope: Scope? = this
        while (scope != null) {
            if (scope.vars.contains(name)) {
                return scope
            }
            scope = scope.parent
        }
        return null
    }

    fun get(name: String): Var {
        val scope = lookup(name)
        return scope?.vars?.get(name) ?: throw IllegalArgumentException("Undefined variable $name")
    }

    fun def(name: String, type: Type): Var {
        if (vars.containsKey(name)) {
            throw IllegalArgumentException("Variable $name is already defined")
        }
        val v = Var(index = counter.incrementAndGet(), type = type)
        vars[name] = v
        return v
    }

}

data class Var(
    val index: Int,
    val type: Type,
)

fun createGlobalScope() = Scope(TreeMap(), parent = null, counter = AtomicInteger(1))
