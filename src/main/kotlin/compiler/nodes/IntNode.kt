package compiler.nodes

import compiler.Context
import vm.operations.IntChar
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
    override fun sameAs(type: Type): Boolean {
        return type is IntType
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "str" -> IntStrProp
            "char" -> IntCharProp
            else -> null
        }
    }

    override fun log() = toString()
}

object IntStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(IntStr())
        return StringType
    }
}

object IntCharProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(IntChar())
        return StringType
    }
}
