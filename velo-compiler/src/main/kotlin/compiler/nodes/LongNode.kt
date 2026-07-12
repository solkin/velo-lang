package compiler.nodes

import core.Op

import compiler.Context

data class LongNode(
    val value: Long,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return LongType
    }
}

object LongType : Numeric {
    // Strict, like the other numerics: widening (int -> long) is inserted by
    // `coerceNumeric` at value sites; narrowing (long -> int) needs `.int()`.
    override fun sameAs(type: Type): Boolean {
        return type is LongType
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = 0L))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "shl" -> LongShlProp
            "shr" -> LongShrProp
            "str" -> NumStrProp
            "long" -> NumIdentityProp
            "int" -> ToIntProp
            "float" -> ToFloatProp
            "byte" -> ToByteProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = core.VmType.Long

    override fun name() = "long"
}

object LongShlProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.Shl)
        return LongType
    }
}

object LongShrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.Shr)
        return LongType
    }
}
