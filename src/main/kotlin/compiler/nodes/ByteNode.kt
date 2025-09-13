package compiler.nodes

import compiler.Context
import vm.operations.IntChar
import vm.operations.IntStr
import vm.operations.Push

data class ByteNode(
    val value: Byte,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return ByteType
    }
}

object ByteType : Numeric {
    override fun sameAs(type: Type): Boolean {
        return type is Numeric
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "str" -> ByteStrProp
            "char" -> ByteCharProp
            else -> null
        }
    }

    override fun log() = toString()

    override fun vmType() = vm.VmByte()
}

object ByteStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(IntStr())
        return StringType
    }
}

object ByteCharProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(IntChar())
        return StringType
    }
}
