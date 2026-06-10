package compiler.nodes

import compiler.Context

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (left !is AssignableNode) throw IllegalArgumentException("Cannot assign to $left")
        val type = right.compile(ctx)
        left.compileAssignment(type, ctx)
        return VoidType
    }
}

interface AssignableNode {
    fun compileAssignment(type: Type, ctx: Context)
}
