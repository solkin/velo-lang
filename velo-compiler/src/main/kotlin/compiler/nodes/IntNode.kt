package compiler.nodes

import core.Op

import compiler.Context

data class IntNode(
    val value: Int,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return IntType
    }
}

object IntType : Numeric {
    override fun sameAs(type: Type): Boolean {
        return type is Numeric
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "shl" -> IntShlProp
            "shr" -> IntShrProp
            "str" -> IntStrProp
            "char" -> IntCharProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = core.VmType.Int

    override fun name() = "int"
}

object IntShlProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.Shl)
        return IntType
    }
}

object IntShrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.Shr)
        return IntType
    }
}

object IntStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.IntStr)
        return StringType
    }
}

object IntCharProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.IntChar)
        return StringType
    }
}
