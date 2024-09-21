package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Push

data class PairNode(
    val first: Node,
    val second: Node?,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = PairValue(
        first = first.evaluate(env),
        second = second?.evaluate(env) ?: VoidValue()
    )
}

data class PairType(val first: Type, val second: Type) : Type {
    override val type: BaseType
        get() = BaseType.PAIR

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
        ctx.add(Push(value = 0))
    }
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
