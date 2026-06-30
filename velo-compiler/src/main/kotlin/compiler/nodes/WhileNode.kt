package compiler.nodes

import core.Op

import compiler.Context

data class WhileNode(
    val cond: Node,
    val expr: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val condCtx = ctx.block()
        cond.compile(condCtx)
        val condLen = condCtx.size()

        val exprCtx = ctx.block()
        exprCtx.loopBody = true
        val type = expr.compile(exprCtx)

        // Backpatch break/continue placeholders now that the body is laid out.
        // `break`    -> just past the loop (skip the rest of the body + back-jump).
        // `continue` -> back to the condition for the next iteration.
        val body = exprCtx.operations()
        val bodyLen = body.size
        for (i in body.indices) {
            val op = body[i]
            if (op is Op.Move && op.count == LOOP_BREAK_MARKER) {
                exprCtx.replace(i, Op.Move(count = bodyLen - i))
            } else if (op is Op.Move && op.count == LOOP_CONTINUE_MARKER) {
                exprCtx.replace(i, Op.Move(count = -(condLen + i + 2)))
            }
        }

        exprCtx.add(Op.Move(-(exprCtx.size() + condCtx.size() + 2))) // +2 because to move and if is not included

        ctx.merge(condCtx)
        ctx.add(Op.If(exprCtx.size()))
        ctx.merge(exprCtx)

        return type
    }
}
