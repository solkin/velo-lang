package compiler.nodes

import compiler.Context
import vm.operations.ActorSpawn
import vm.operations.Call

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        // Special-case: `new ActorClass(args)` becomes an ActorSpawn op.
        // We intercept BEFORE compiling `func` so we don't emit a spurious
        // Load of the class frame — ActorSpawn carries the frame number itself.
        if (func is VarNode) {
            val resolved = ctx.opt(func.name)?.type as? ClassType
            if (resolved != null && resolved.isActor) {
                return compileActorSpawn(resolved, ctx)
            }
        }

        val argTypes = args.map { arg ->
            arg.compile(ctx)
        }
        val returnType = func.compile(ctx)
        if (returnType is Callable) {
            // [funcArgTypes] is `null` when the callable's declared type does
            // not carry argument information — most commonly the loose form
            // `func[T]` (only the return type is known) used for higher-order
            // parameters, callbacks and stored function values. In that case
            // we skip compile-time arity / argument type checks and trust the
            // caller. The strict-typed path below is preserved for fully
            // typed callables where `args` is non-null.
            val funcArgTypes = returnType.args
            if (funcArgTypes != null) {
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

    private fun compileActorSpawn(classType: ClassType, ctx: Context): Type {
        val frameNum = classType.num
            ?: throw IllegalStateException("Actor class '${classType.name}' has no frame number")
        val expected = classType.args
        if (expected != null) {
            if (expected.size != args.size) {
                throw IllegalArgumentException(
                    "Actor '${classType.name}' constructor expects ${expected.size} args, got ${args.size}"
                )
            }
        }
        val argTypes = args.map { it.compile(ctx) }
        if (expected != null) {
            expected.forEachIndexed { i, def ->
                val actual = argTypes[i]
                if (!actual.sameAs(def)) {
                    throw IllegalArgumentException(
                        "Actor '${classType.name}' constructor arg #${i + 1}: " +
                            "expected ${def.log()}, got ${actual.log()}"
                    )
                }
            }
        }
        ctx.add(ActorSpawn(classFrameNum = frameNum, className = classType.name, args = args.size))
        return ActorBoundType(classType)
    }
}
