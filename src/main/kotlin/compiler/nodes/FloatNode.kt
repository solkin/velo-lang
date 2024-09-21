package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Push

data class FloatNode(
    val value: Double,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = FloatValue(value)

    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return FloatType
    }
}

object FloatType : Type {
    override val type: BaseType
        get() = BaseType.FLOAT

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0f))
    }
}

class FloatValue(val value: Double) : Value<Double>(value)
