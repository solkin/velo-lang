package compiler.nodes

import core.Op

import compiler.Context

data class ByteNode(
    val value: Byte,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return ByteType
    }
}

object ByteType : Numeric {
    override fun sameAs(type: Type): Boolean {
        return type is ByteType
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "str" -> ByteStrProp
            "char" -> ByteCharProp
            "byte" -> NumIdentityProp
            "int" -> ByteToIntProp
            "long" -> IntToLongProp
            "float" -> IntToFloatProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = core.VmType.Byte

    override fun name() = "byte"
}

object ByteStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.IntStr)
        return StringType
    }
}

object ByteCharProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.IntChar)
        return StringType
    }
}
