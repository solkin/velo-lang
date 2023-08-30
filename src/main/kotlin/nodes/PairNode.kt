package nodes

import Environment

data class PairNode(
    val first: Node,
    val second: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = PairType(
        first = first.evaluate(env),
        second = second?.evaluate(env) ?: VoidType()
    )
}

class PairType(val first: Type<*>, val second: Type<*>) : Type<Pair<Type<*>, Type<*>>>(t = first to second) {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "first" -> first
            "second" -> second
            else -> super.property(name, args)
        }
    }
}
