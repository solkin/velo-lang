package compiler.nodes

import compiler.Context
import vm.operations.Push
import vm.operations.StrCon
import vm.operations.StrIndex
import vm.operations.StrInt
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

object StringType : Indexable {
    override fun sameAs(type: Type): Boolean {
        return type is StringType
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = ""))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "sub" -> SubStrProp
            "len" -> StrLenProp
            "con" -> StrConProp
            "int" -> StrIntProp
            else -> null
        }
    }

    override fun log() = toString()

    override fun vmType() = vm.VmStr()

    override fun compileIndex(ctx: Context): Type {
        ctx.add(StrIndex())
        return IntType
    }

    override fun name() = "str"
}

object SubStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(SubStr())
        return StringType
    }
}

object StrLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(StrLen())
        return IntType
    }
}

object StrConProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(StrCon())
        return StringType
    }
}

object StrIntProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(StrInt())
        return IntType
    }
}
