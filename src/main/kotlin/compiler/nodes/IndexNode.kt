package compiler.nodes

import compiler.Context
import vm.operations.ArrIndex
import vm.operations.StrIndex

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val type = list.compile(ctx)
        index.compile(ctx)
        return when (type) {
            is ArrayType -> {
                ctx.add(ArrIndex())
                type.derived
            }

            is StringType -> {
                ctx.add(StrIndex())
                IntType
            }

            else -> throw IllegalArgumentException("Index on non-indexable type $type")
        }
    }
}
