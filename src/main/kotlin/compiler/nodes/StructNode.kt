package compiler.nodes

import compiler.Context

data class StructNode(
    val name: String,
    val nodes: List<DefNode>,
) : Node() {
    override fun compile(ctx: Context): Type {
        return super.compile(ctx)
    }
}
