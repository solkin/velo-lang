package compiler.nodes

import compiler.Context

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val type = ctx.wrapScope { context ->
            vars.forEach { it.compile(context) }
            val type = body.compile(context)
            type
        }

        return type
    }
}
