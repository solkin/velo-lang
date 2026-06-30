package compiler.nodes

import core.Op

import compiler.Context

/**
 * `let name = expr` — an immutable local whose type is inferred from the
 * initializer. The one place Velo infers a type (signatures stay explicit);
 * reassigning a `let` binding is a compile error, the same guarantee a
 * `data class` field has.
 */
data class LetNode(
    val name: String,
    val value: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val type = value.compile(ctx)
        val variable = ctx.def(name, type, immutable = true)
        ctx.add(Op.Store(variable.index))
        return VoidType
    }
}
