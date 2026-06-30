package compiler.nodes

import core.Op

import compiler.Context

/**
 * `break` and `continue` compile to a placeholder [Op.Move] carrying a sentinel
 * distance. The enclosing loop ([WhileNode]/[ForNode]) scans its assembled body
 * once and backpatches each marker to the real jump distance — to just past the
 * loop for `break`, to the loop's continue point (the condition, or a `for`'s
 * step) for `continue`. A real jump distance can never collide with these
 * sentinels. Nested loops resolve innermost-first, so each loop only ever sees
 * its own markers.
 */
const val LOOP_BREAK_MARKER = Int.MIN_VALUE
const val LOOP_CONTINUE_MARKER = Int.MIN_VALUE + 1

object BreakNode : Node() {
    override fun compile(ctx: Context): Type {
        if (!ctx.isInLoop()) {
            throw IllegalStateException("'break' is only allowed inside a loop")
        }
        ctx.add(Op.Move(count = LOOP_BREAK_MARKER))
        return VoidType
    }
}

object ContinueNode : Node() {
    override fun compile(ctx: Context): Type {
        if (!ctx.isInLoop()) {
            throw IllegalStateException("'continue' is only allowed inside a loop")
        }
        ctx.add(Op.Move(count = LOOP_CONTINUE_MARKER))
        return VoidType
    }
}
