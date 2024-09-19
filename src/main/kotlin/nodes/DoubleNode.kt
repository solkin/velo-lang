package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class DoubleNode(
    val value: Double,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = DoubleValue(value)

    override fun compile(ctx: CompilerContext): Type {
        ctx.add(Push(value))
        return FloatType
    }
}

class DoubleValue(val value: Double) : Value<Double>(value)
