package compiler.nodes

import compiler.Context
import vm.operations.If
import vm.operations.Move

data class IfNode(
    val condNode: Node,
    val thenNode: ScopeNode,
    val elseNode: ScopeNode?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val thenCtx = ctx.inner()
        val thenType = thenNode.compile(thenCtx)

        val elseCtx = ctx.inner()
        val elseType = elseNode?.let { elseNode ->
            val type = elseNode.compile(elseCtx)
            thenCtx.add(Move(elseCtx.size()))
            type
        }

        if (elseType != null && thenType.type != elseType.type) {
            throw IllegalArgumentException("Then and else return types are differ: $thenType / $elseType")
        }

        condNode.compile(ctx)
        val elseSkip = thenCtx.size()
        ctx.add(If(elseSkip))
        ctx.merge(thenCtx)
        if (elseCtx.isNotEmpty()) {
            ctx.merge(elseCtx)
        }
        return thenType
    }
}
