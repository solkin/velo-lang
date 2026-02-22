package compiler.nodes

import compiler.Context
import vm.operations.Push

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return BoolType
    }
}

object BoolType : Type {
    override fun sameAs(type: Type): Boolean {
        return type is BoolType
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = false))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = name()

    override fun vmType() = vm.VmType.Bool

    override fun name() = "bool"
}
