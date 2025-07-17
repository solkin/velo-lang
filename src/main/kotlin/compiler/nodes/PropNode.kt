package compiler.nodes

import compiler.Context

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        return ctx.wrapScope { scopeCtx ->
            val parentType = parent.compile(scopeCtx)
            val argsType = args.orEmpty().reversed().map { it.compile(scopeCtx) }
            val prop = parentType.prop(name)
                ?: throw IllegalArgumentException("Property '$name' of ${parentType.log()} is not supported")
            prop.compile(parentType, args = argsType, scopeCtx)
        }
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val parentType = parent.compile(ctx)
        val argsType = args.orEmpty().reversed().map { it.compile(ctx) }
        val prop = parentType.prop(name)
            ?: throw IllegalArgumentException("Property '$name' of ${parentType.log()} is not supported")
        if (prop !is AssignableProp) {
            throw IllegalArgumentException("Cannot assign to non-assignable prop '$name' of type $prop")
        }
        prop.compileAssignment(parentType, type, argsType, ctx)
        VoidType
    }
}
