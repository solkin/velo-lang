package compiler.nodes

import core.Op

import compiler.Context

/**
 * An explicit `return`. Because control flow is compiled inline (no closure
 * frames for `if`/`while` bodies), [Op.Ret] here returns from the enclosing
 * function frame directly, so an early return from inside a branch or loop
 * works at any nesting. The returned value's type is checked against the
 * function's declared return type, found by walking the context chain.
 */
data class ReturnNode(
    val value: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val expected = ctx.enclosingReturnType()
            ?: throw IllegalStateException("'return' is only allowed inside a function")

        if (value == null) {
            if (expected !is VoidType && expected !is SelfType) {
                throw IllegalStateException("'return' without a value in a function returning ${expected.log()}")
            }
            ctx.add(Op.Ret)
            return VoidType
        }

        val type = value.compile(ctx)
        // `Self` returns are resolved at the call site, like a fall-through body.
        if (expected !is SelfType && !type.sameAs(expected) && !expected.sameAs(AnyType)) {
            throw IllegalStateException(
                "return type ${type.log()} does not match function return type ${expected.log()}"
            )
        }
        ctx.add(Op.Ret)
        return type
    }
}
