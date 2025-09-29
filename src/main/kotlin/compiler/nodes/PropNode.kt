package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.Get

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        return ctx.wrapScope { scopeCtx ->
            val parentType = parent.compile(scopeCtx)
            val ext = scopeCtx.opt(parentType.name() + "@" + name)
            if (ext != null) {
                val argTypes = listOf(parentType) + args.orEmpty().map { it.compile(scopeCtx) }
                scopeCtx.add(Get(ext.index))
                val returnType = ext.type
                if (returnType !is Callable) throw IllegalArgumentException("Call on non-function type")
                val funcArgTypes = returnType.args ?: throw Exception("Extension arguments is not defined")
                if (funcArgTypes.size != argTypes.size) {
                    throw Exception("Call args count ${argTypes.size} is differ from required ${funcArgTypes.size}")
                }
                funcArgTypes.forEachIndexed { i, def ->
                    val argType = argTypes[i]
                    if (!argType.sameAs(def)) {
                        throw Exception("Argument \"${argType.log()}\" is differ from required type ${def.log()}")
                    }
                }
                val type = when (returnType) {
                    is FuncType -> returnType.derived
                    else -> throw IllegalArgumentException("Call on non-function type")
                }
                scopeCtx.add(Call(argTypes.size))
                type
            } else {
                val argTypes = args.orEmpty().reversed().map { it.compile(scopeCtx) }
                val prop = parentType.prop(name)
                    ?: throw IllegalArgumentException("Property '$name' of ${parentType.log()} is not supported")
                prop.compile(parentType, args = argTypes, scopeCtx)
            }
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
