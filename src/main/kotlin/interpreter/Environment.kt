package interpreter

import parser.Node

class Environment internal constructor(
    private val vars: MutableMap<String, Node>,
    private val parent: Environment?
) {

    fun extend() = Environment(HashMap(vars), this)

    fun lookup(name: String): Environment? {
        var scope: Environment? = this
        while (scope != null) {
            if (scope.vars.containsKey(name)) {
                return scope
            }
            scope = scope.parent
        }
        return null
    }

    fun get(name: String): Node {
        val node = vars[name]
        if (node != null) {
            return node
        }
        throw IllegalArgumentException("Undefined variable $name")
    }

    fun set(name: String, value: Node): Node {
        val scope = lookup(name)
        if (scope == null && parent != null) {
            throw IllegalArgumentException("Undefined variable $name")
        }
        (scope ?: this).vars[name] = value
        return value
    }

    fun def(name: String, value: Node) {
        vars[name] = value
    }

}

fun createGlobalEnvironment() = Environment(HashMap(), null)
