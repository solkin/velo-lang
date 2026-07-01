package compiler.nodes

import core.Op

import compiler.Context

data class FloatNode(
    val value: Float,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return FloatType
    }
}

object FloatType : Numeric {
    override fun sameAs(type: Type): Boolean {
        return type is FloatType
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = 0f))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "str" -> FloatStrProp
            "float" -> NumIdentityProp
            "long" -> FloatToLongProp
            "int" -> FloatToIntProp
            "byte" -> FloatToByteProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = core.VmType.Float

    override fun name() = "float"
}

object FloatStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.FloatStr)
        return StringType
    }
}
