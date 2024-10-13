package compiler.nodes

import compiler.Context
import vm.operations.Push

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return BoolType
    }
}

object BoolType : Type {
    override val type: BaseType
        get() = BaseType.BOOLEAN

    override fun default(ctx: Context) {
        ctx.add(Push(value = false))
    }
}
