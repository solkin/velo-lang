package nodes

import Environment

data class PairNode(
    val first: Node,
    val second: Node?,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = PairValue(
        first = first.evaluate(env),
        second = second?.evaluate(env) ?: VoidValue()
    )
}

class PairValue(val first: Value<*>, val second: Value<*>) : Value<Pair<Value<*>, Value<*>>>(t = first to second) {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "first" -> first
            "second" -> second
            else -> super.property(name, args)
        }
    }
}
