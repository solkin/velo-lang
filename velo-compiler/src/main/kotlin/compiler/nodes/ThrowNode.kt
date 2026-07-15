package compiler.nodes

import core.Op

import compiler.Context

/**
 * A `throw expr` statement. Raises an `Error`: the value is pushed and
 * [Op.Throw] unwinds to the nearest active `try` handler (or stops the program
 * if none).
 *
 * Shorthand: `throw "text"` — a bare string literal — lowers to
 * `throw new Error(ERR_GENERIC, "text")`, the common case. Any other value must
 * already be an `Error`. `throw e` inside a `catch` re-raises the caught value.
 */
data class ThrowNode(
    val value: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val toThrow: Node = if (value is StringNode) {
            CallNode(func = VarNode(name = "Error"), args = listOf(VarNode(name = "ERR_GENERIC"), value))
        } else {
            value
        }
        val type = toThrow.compile(ctx)
        if (type !is ClassType || type.name != "Error") {
            throw IllegalStateException(
                "'throw' requires an Error value (or a string literal), got ${type.log()}"
            )
        }
        ctx.add(Op.Throw)
        return VoidType
    }
}
