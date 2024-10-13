package compiler.nodes

import compiler.Context
import vm.operations.IntStr
import vm.operations.Push

data class IntNode(
    val value: Int,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return IntType
    }
}

object IntType : Type {
    override val type: BaseType
        get() = BaseType.INT

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }
}

object IntStrProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(IntStr())
        return StringType
    }
}
