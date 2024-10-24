package compiler.nodes

import compiler.Context
import vm.operations.Push

data class FloatNode(
    val value: Double,
) : Node() {
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

    override fun prop(name: String): Prop? = null
}
