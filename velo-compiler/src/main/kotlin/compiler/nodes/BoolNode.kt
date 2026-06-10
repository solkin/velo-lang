package compiler.nodes

import core.Op

import compiler.Context

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Op.Push(value))
        return BoolType
    }
}

object BoolType : Type {
    override fun sameAs(type: Type): Boolean {
        return type is BoolType
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = false))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = name()

    override fun vmType() = core.VmType.Bool

    override fun name() = "bool"
}
