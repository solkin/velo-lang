package compiler.nodes

import compiler.Context
import vm.operations.Call

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        val argTypes = args.map { arg ->
            arg.compile(ctx)
        }
        val returnType = func.compile(ctx)
        if (returnType is Callable) {
            val funcArgTypes = returnType.args ?: throw Exception("Callable type arguments is not defined")
            if (funcArgTypes.size != argTypes.size) {
                throw Exception("Call args count ${argTypes.size} is differ from required ${funcArgTypes.size}")
            }
            val resolvedArgTypes = if (returnType is FuncType) {
                resolveFuncArgTypes(returnType, funcArgTypes, argTypes)
            } else {
                funcArgTypes
            }
            resolvedArgTypes.forEachIndexed { i, def ->
                val argType = argTypes[i]
                if (!argType.sameAs(def)) {
                    throw Exception("Argument \"${argType.log()}\" is differ from required type ${def.log()}")
                }
            }
        }
        if (returnType is ClassType && returnType.typeArgs.isNotEmpty()) {
            val classArgTypes = returnType.args ?: emptyList()
            val resolvedArgTypes = classArgTypes.map { returnType.resolveGeneric(it) }
            resolvedArgTypes.forEachIndexed { i, def ->
                if (i < argTypes.size) {
                    val argType = argTypes[i]
                    if (!argType.sameAs(def)) {
                        throw Exception("Argument \"${argType.log()}\" is differ from required type ${def.log()}")
                    }
                }
            }
        }
        val type = when (returnType) {
            is FuncType -> resolveReturnType(returnType, argTypes)
            is ClassType -> returnType
            else -> throw IllegalArgumentException("Call on non-function type")
        }
        ctx.add(Call(args.size))
        return type
    }

    private fun resolveFuncArgTypes(
        funcType: FuncType,
        funcArgTypes: List<Type>,
        actualArgTypes: List<Type>,
    ): List<Type> {
        if (funcType.typeParams.isEmpty()) return funcArgTypes
        val bindings = inferTypeBindings(funcType.typeParams, funcArgTypes, actualArgTypes)
        return funcArgTypes.map { resolveGenericType(it, funcType.typeParams, bindings) }
    }

    private fun resolveReturnType(funcType: FuncType, actualArgTypes: List<Type>): Type {
        if (funcType.typeParams.isEmpty()) return funcType.derived
        val funcArgTypes = funcType.args ?: return funcType.derived
        val bindings = inferTypeBindings(funcType.typeParams, funcArgTypes, actualArgTypes)
        return resolveGenericType(funcType.derived, funcType.typeParams, bindings)
    }
}
