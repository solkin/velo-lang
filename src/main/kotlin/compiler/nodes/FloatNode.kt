package compiler.nodes

import compiler.Context
import vm.operations.Push

data class FloatNode(
    val value: Float,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return FloatType
    }
}

object FloatType : Numeric {
    override fun sameAs(type: Type): Boolean {
        return type is Numeric
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0f))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()

    override fun vmType() = vm.FLOAT
}
