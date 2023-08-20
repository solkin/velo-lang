class Environment<T> internal constructor(
    private val vars: MutableMap<String, T>,
    private val parent: Environment<T>?
) {

    fun extend() = Environment(HashMap(vars), this)

    fun lookup(name: String): Environment<T>? {
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
        val node = vars[name]
        if (node != null) {
            return node
        }
        throw IllegalArgumentException("Undefined variable $name")
    }

    fun set(name: String, value: T): T {
        val scope = lookup(name)
        (scope ?: this).vars[name] = value
        return value
    }

    fun def(name: String, value: T) {
        vars[name] = value
    }

}

fun <T>createGlobalEnvironment() = Environment<T>(HashMap(), null)
