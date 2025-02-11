package compiler.nodes

import compiler.Context

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node() {
    override fun compile(ctx: Context): Type {
        return ctx.wrapScope { scopeCtx ->
            val parentType = parent.compile(scopeCtx)
            val argsType = args.orEmpty().reversed().map { it.compile(scopeCtx) }
            val prop = parentType.prop(name) ?: throw IllegalArgumentException("Property '$name' of ${parentType.type} is not supported")
            prop.compile(parentType, args = argsType, scopeCtx)
        }
    }
}
