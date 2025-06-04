package compiler.nodes

import compiler.Context
import vm.operations.Pair
import vm.operations.PairFirst
import vm.operations.PairSecond
import vm.operations.Push

data class PairNode(
    val first: Node,
    val second: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val firstType = first.compile(ctx)
        val secondType = second?.compile(ctx) ?: VoidType
        ctx.add(Pair())
        return PairType(firstType, secondType)
    }
}

data class PairType(val first: Type, val second: Type) : Type {
    override fun sameAs(type: Type): Boolean {
        return type is PairType && type.first.sameAs(first) && type.second.sameAs(second)
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "first" -> FirstProp
            "second" -> SecondProp
            else -> null
        }
    }

    override fun log() = toString()
}

object FirstProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? PairType ?: throw IllegalArgumentException("Pair operation on non-pair type $type")
        ctx.add(PairFirst())
        return type.first
    }
}

object SecondProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? PairType ?: throw IllegalArgumentException("Pair operation on non-pair type $type")
        ctx.add(PairSecond())
        return type.second
    }
}
