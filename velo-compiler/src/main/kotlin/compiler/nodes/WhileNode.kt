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

        // Body-local slots span [scopeBase, scopeBase + scopeCount).
        val scopeBase = ctx.frame.varCounter.get()
        val exprCtx = ctx.block()
        exprCtx.loopBody = true
        val type = expr.compile(exprCtx)
        val scopeCount = ctx.frame.varCounter.get() - scopeBase

        // Give the body a fresh per-iteration scope only when it declares locals
        // AND creates a closure (an `Op.Frame`) — otherwise the shared frame is
        // fine and we keep the loop flat (zero overhead).
        val body = exprCtx.operations()
        val bodyLen = body.size
        val scoped = scopeCount > 0 && body.any { it is Op.Frame }

        backpatchLoop(
            exprCtx, scoped,
            breakDist = { m -> bodyLen - m + 1 },      // jump past the loop
            contDist = { m -> -(condLen + m + 3) },    // jump back to the condition
        )

        // Uniform layout (PRE/POST are ScopeEnter/Leave when scoped, else no-op
        // Move(0), so op counts and jump distances don't depend on `scoped`):
        //   [cond] If [PRE] [body] [POST] [back]
        ctx.merge(condCtx)
        ctx.add(Op.If(elseSkip = bodyLen + 3))
        ctx.add(if (scoped) Op.ScopeEnter(scopeBase, scopeCount) else Op.Move(count = 0))
        ctx.merge(exprCtx)
        ctx.add(if (scoped) Op.ScopeLeave else Op.Move(count = 0))
        ctx.add(Op.Move(count = -(condLen + bodyLen + 4)))

        return type
    }
}

/**
 * Backpatch a loop body's break/continue placeholders. Each is a two-op pair
 * `[scope-leave placeholder][jump marker]`; the placeholder becomes
 * [Op.ScopeLeave] (scoped) or a no-op `Move(0)`, and the marker becomes a jump
 * of [breakDist]/[contDist] (each a function of the marker's index `m`). The
 * targets differ per loop kind: `continue` goes to the condition in a `while`
 * but to the increment step in a `for`.
 */
internal fun backpatchLoop(
    bodyCtx: Context,
    scoped: Boolean,
    breakDist: (Int) -> Int,
    contDist: (Int) -> Int,
) {
    val body = bodyCtx.operations()
    val leave = if (scoped) Op.ScopeLeave else Op.Move(count = 0)
    for (m in body.indices) {
        val op = body[m]
        if (op is Op.Move && op.count == LOOP_BREAK_MARKER) {
            bodyCtx.replace(m - 1, leave)
            bodyCtx.replace(m, Op.Move(count = breakDist(m)))
        } else if (op is Op.Move && op.count == LOOP_CONTINUE_MARKER) {
            bodyCtx.replace(m - 1, leave)
            bodyCtx.replace(m, Op.Move(count = contDist(m)))
        }
    }
}
