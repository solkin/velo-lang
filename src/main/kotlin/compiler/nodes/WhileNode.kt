package compiler.nodes

import compiler.Context
import vm.operations.If
import vm.operations.Move

data class WhileNode(
    val cond: Node,
    val expr: ScopeNode,
) : Node() {
    override fun compile(ctx: Context): Type {
        val condCtx = ctx.inner()
        cond.compile(condCtx)

        val exprCtx = ctx.inner()
        val type = expr.compile(exprCtx)
        exprCtx.add(Move(-(exprCtx.size() + condCtx.size() + 2))) // +2 because to move and if is not included

        ctx.merge(condCtx)
        ctx.add(If(exprCtx.size()))
        ctx.merge(exprCtx)

        return type
    }
}
