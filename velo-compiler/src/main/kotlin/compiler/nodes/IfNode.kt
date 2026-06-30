package compiler.nodes

import core.Op

import compiler.Context

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        // Branches compile inline (sub-frames sharing the enclosing function
        // frame), not as closures invoked by Call. Keeping the branch ops in
        // the same frame is what lets a `return`/`break`/`continue` inside a
        // branch jump straight out — a closure boundary would trap the exit.
        condNode.compile(ctx)

        val thenCtx = ctx.block()
        val thenType = thenNode.compile(thenCtx)

        if (elseNode != null) {
            val elseCtx = ctx.block()
            val elseType = elseNode.compile(elseCtx)

            // [cond] If(then+1) [then] Move(else) [else]
            // cond true  -> run then, then Move skips else
            // cond false -> If skips then + the Move, lands on else
            ctx.add(Op.If(elseSkip = thenCtx.size() + 1))
            ctx.merge(thenCtx)
            ctx.add(Op.Move(count = elseCtx.size()))
            ctx.merge(elseCtx)

            return if (thenType.sameAs(elseType)) thenType else AnyType
        }

        // No else: skip the then-block when the condition is false.
        ctx.add(Op.If(elseSkip = thenCtx.size()))
        ctx.merge(thenCtx)
        return thenType
    }
}
