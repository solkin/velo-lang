package compiler.nodes

import compiler.Context
import vm.operations.Index

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val type = list.compile(ctx)
        type as? ArrayType ?: throw IllegalArgumentException("Index on non-indexable type $type")
        index.compile(ctx)
        ctx.add(Index())
        return type.derived
    }
}
