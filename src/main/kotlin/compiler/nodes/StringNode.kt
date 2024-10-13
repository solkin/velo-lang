package compiler.nodes

import compiler.Context
import vm.operations.Push
import vm.operations.StrCon
import vm.operations.StrLen
import vm.operations.SubStr

data class StringNode(
    val value: String,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return StringType
    }
}

object StringType : Type {
    override val type: BaseType
        get() = BaseType.STRING

    override fun default(ctx: Context) {
        ctx.add(Push(value = ""))
    }
}

object SubStrProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(SubStr())
        return StringType
    }
}

object StrLenProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(StrLen())
        return IntType
    }
}

object StrConProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(StrCon())
        return StringType
    }
}
