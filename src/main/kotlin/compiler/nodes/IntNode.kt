package compiler.nodes

import compiler.Context
import vm.operations.IntChar
import vm.operations.IntStr
import vm.operations.Push
import vm.operations.Shl
import vm.operations.Shr

data class IntNode(
    val value: Int,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return IntType
    }
}

object IntType : Numeric {
    override fun sameAs(type: Type): Boolean {
        return type is Numeric
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
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

    override fun log() = toString()

    override fun vmType() = vm.VmInt()

    override fun name() = "int"
}

object IntShlProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Shl())
        return IntType
    }
}

object IntShrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Shr())
        return IntType
    }
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
