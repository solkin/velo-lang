package compiler.nodes

import compiler.Environment

class SubjectNode(
    private val init: Node?
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        val initValue = init?.evaluate(env) ?: BoolValue(value = false)
        return SubjectValue(initValue)
    }
}

class SubjectValue(var value: Value<*>) : Value<Value<*>>(value) {

    private val observers = ArrayList<FuncValue>()

    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "observe" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'observe' requires observer func argument")
                }
                val observer = args[0] as FuncValue
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
                val observer = args[0] as FuncValue
                val result = observers.remove(observer)
                BoolValue(result)
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
