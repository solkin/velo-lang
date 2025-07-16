package compiler.nodes

import compiler.Context

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        val type = list.compile(ctx)
        index.compile(ctx)
        if (type !is Indexable) {
            throw IllegalArgumentException("Index on non-indexable type $type")
        }
        return type.compileIndex(ctx)
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val type = list.compile(ctx)
        index.compile(ctx)
        if (type !is IndexAssignable) {
            throw IllegalArgumentException("Assign on non-assignable index type $type")
        }
        type.compileAssignment(ctx)
    }
}
