package compiler.nodes

import compiler.Context
import vm.operations.Set

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        val type = right.compile(ctx)
        val v = ctx.get(left.name)
        if (!v.type.sameAs(type)) {
            throw IllegalArgumentException("Illegal assign type $type != ${v.type}")
        }
        ctx.add(Set(v.index))
        return VoidType
    }
}
