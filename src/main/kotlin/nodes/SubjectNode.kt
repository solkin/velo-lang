package nodes

import Environment

class SubjectNode(
    private val init: Node?
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val initValue = init?.evaluate(env) ?: BoolType(value = false)
        return SubjectType(initValue)
    }
}

class SubjectType(var value: Type<*>) : Type<Type<*>>(value) {

    private val observers = ArrayList<FuncType>()

    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "observe" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'observe' requires observer func argument")
                }
                val observer = args[0] as FuncType
                observers.add(observer)
                observer
            }

            "get" -> value
            "set" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'set' requires value argument")
                }
                val value = args[0]
                this.value = value
                notifyObservers()
                this
            }

            "detach" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'detach' requires observer func argument")
                }
                val observer = args[0] as FuncType
                val result = observers.remove(observer)
                BoolType(result)
            }

            "notify" -> {
                notifyObservers()
                this
            }

            else -> super.property(name, args)
        }
    }

    private fun notifyObservers() {
        observers.forEach { it.run(args = listOf(value), it = this) }
    }
}
