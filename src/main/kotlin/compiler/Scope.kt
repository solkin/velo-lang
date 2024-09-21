package compiler

import compiler.nodes.Type
import java.util.TreeMap

class Scope internal constructor(
    private val vars: MutableMap<String, Var>,
    val parent: Scope?
) {

    fun extend() = Scope(TreeMap(), this)

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
        val v = Var(index = vars.size, type = type)
        vars[name] = v
        return v
    }

}

fun createGlobalScope() = Scope(TreeMap(), null)
