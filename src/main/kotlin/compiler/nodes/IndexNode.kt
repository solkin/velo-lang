package compiler.nodes

import compiler.Context

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val type = list.compile(ctx)
        index.compile(ctx)
        if (type !is Indexable) {
            throw IllegalArgumentException("Index on non-indexable type $type")
        }
        return type.compileIndex(ctx)
    }
}
