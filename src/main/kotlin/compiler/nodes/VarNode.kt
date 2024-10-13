package compiler.nodes

import compiler.Context
import vm.operations.Get

data class VarNode(
    val name: String,
) : Node() {
    override fun compile(ctx: Context): Type {
        val v = ctx.enumerator.get(name)
        ctx.add(Get(v.index))
        return v.type
    }
}
