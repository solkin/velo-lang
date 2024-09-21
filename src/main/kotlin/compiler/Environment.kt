package compiler

class Environment<T> internal constructor(
    private val vars: MutableMap<String, T>,
    private val parent: Environment<T>?
) {

    fun extend() = Environment(HashMap(), this)

    private fun lookup(name: String): Environment<T>? {
        var scope: Environment<T>? = this
        while (scope != null) {
            if (scope.vars.containsKey(name)) {
                return scope
            }
            scope = scope.parent
        }
        return null
    }

    fun get(name: String): T {
        val scope = lookup(name)
        return scope?.vars?.get(name) ?: throw IllegalArgumentException("Undefined variable $name")
    }

    fun set(name: String, value: T): T {
        val scope = lookup(name) ?: throw IllegalArgumentException("Undefined variable $name")
        scope.vars[name] = value
        return value
    }

    fun def(name: String, value: T) {
        if (vars.containsKey(name)) throw IllegalArgumentException("Variable $name is already defined")
        vars[name] = value
    }

}

fun <T> createGlobalEnvironment() = Environment<T>(HashMap(), null)
