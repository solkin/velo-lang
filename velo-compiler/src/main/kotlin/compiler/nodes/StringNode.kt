package compiler.nodes

import core.Op

import compiler.Context

data class StringNode(
    val value: String,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return StringType
    }
}

object StringType : Indexable {
    override fun sameAs(type: Type): Boolean {
        return type is StringType
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = ""))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "sub" -> SubStrProp
            "len" -> StrLenProp
            "con" -> StrConProp
            "int" -> StrNumProp(IntType, core.VmType.Int)
            "float" -> StrNumProp(FloatType, core.VmType.Float)
            "long" -> StrNumProp(LongType, core.VmType.Long)
            "str" -> StrStrProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = core.VmType.Str

    override fun compileIndex(ctx: Context): Type {
        ctx.add(Op.StrIndex)
        return IntType
    }

    override fun name() = "str"
}

object SubStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.StrSub)
        return StringType
    }
}

object StrLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.StrLen)
        return IntType
    }
}

object StrConProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.StrCon)
        return StringType
    }
}

/** `str.int()`/`str.float()`/`str.long()` — parse a decimal string (Op.StrNum). */
class StrNumProp(private val target: Type, private val to: core.VmType) : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.StrNum(to))
        return target
    }
}

/** `str.str` is the identity — emit nothing, so interpolation can be uniform. */
object StrStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        return StringType
    }
}
