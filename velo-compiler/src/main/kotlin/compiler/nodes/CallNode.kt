package compiler.nodes

import core.Op

import compiler.Context
import core.NativeClassDescriptor

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
            // `new DataClass(args)` embeds the class frame number (like
            // ActorSpawn) instead of loading the class-name variable, so a data
            // class can be constructed from any scope — including inside an
            // actor, whose thread does not share the declaring frame's vars.
            if (resolved != null && resolved.isData) {
                return compileDataNew(resolved, ctx)
            }
            // `new RegularClass(args)` — embed the class frame number (like the
            // data-class and actor cases above) instead of loading the class-name
            // variable. A bare `Op.Load` of that var is unreachable from inside an
            // actor, whose thread does not share the declaring frame's vars, so
            // `new SomeClass(...)` in an actor method would otherwise fail at
            // runtime with "Undefined variable". Native classes have no Velo var
            // (handled below); only user-declared classes reach here.
            if (resolved != null) {
                return compileClassNew(resolved, func.typeArgs, ctx)
            }
            // `new RegisteredHostClass(args)` — no Velo declaration exists;
            // the type is synthesized from the registry and the construction
            // is a single NativeCall over the program's native pool. A local
            // variable with the same name shadows the native class.
            if (ctx.opt(func.name) == null) {
                ctx.shared.descriptor(func.name)?.let { descriptor ->
                    return compileNativeNew(descriptor, ctx)
                }
            }
        }

        val argTypes = args.map { arg ->
            arg.compile(ctx)
        }
        val returnType = compileCallable(ctx)
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
                    if (!assignableArg(def, argType)) {
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
                    if (!assignableArg(def, argType)) {
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
        // A value-returning callable, when it turns out to be a foreign actor
        // callback at runtime, must be invoked as a blocking cross-actor call
        // rather than fire-and-forget (see Op.Call). Harmless for local calls.
        val callbackResult = returnType is FuncType && !returnType.derived.sameAs(VoidType)
        ctx.add(Op.Call(args.size, callbackResult = callbackResult))
        return type
    }

    /**
     * Push the callable. A direct call of a **free-standing** function (one that
     * captures no enclosing scope) emits its frame by number (`Op.Frame`) instead
     * of loading the declaring variable, so the call runs from an actor thread too
     * — the variable may live in a frame the actor does not share, whereas the
     * frame table is program-wide (the same reason `new Class` embeds its frame
     * number). A closure, a stored function value, or a higher-order parameter
     * carries a captured scope, so it goes through the normal variable path.
     */
    private fun compileCallable(ctx: Context): Type {
        if (func is VarNode) {
            ctx.directFuncNum(func.name)?.let { num ->
                ctx.add(Op.Frame(num = num))
                return ctx.get(func.name).type
            }
        }
        return func.compile(ctx)
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

    private fun compileNativeNew(descriptor: NativeClassDescriptor, ctx: Context): Type {
        val expected = descriptor.ctorParams.map { vmTypeToType(it, ctx) }
        if (expected.size != args.size) {
            throw IllegalArgumentException(
                "Native class '${descriptor.veloName}' constructor expects ${expected.size} args, got ${args.size}"
            )
        }
        // Push args in reverse so the first argument ends up on top —
        // the same stack shape NativeCall sees for method dispatch.
        val actual = args.reversed().map { it.compile(ctx) }.reversed()
        expected.forEachIndexed { i, exp ->
            if (!exp.sameAs(actual[i])) {
                throw IllegalArgumentException(
                    "Native class '${descriptor.veloName}' constructor arg #${i + 1}: " +
                        "expected ${exp.log()}, got ${actual[i].log()}"
                )
            }
        }
        val index = ctx.shared.intern(descriptor.constructorRef())
        ctx.add(Op.NativeCall(poolIndex = index, args = callSiteVmTypes(descriptor.ctorParams, actual)))
        return NativeClassType(descriptor)
    }

    private fun compileDataNew(classType: ClassType, ctx: Context): Type {
        val frameNum = classType.num
            ?: throw IllegalStateException("Data class '${classType.name}' has no frame number")
        val expected = classType.args
        if (expected != null && expected.size != args.size) {
            throw IllegalArgumentException(
                "Data class '${classType.name}' constructor expects ${expected.size} args, got ${args.size}"
            )
        }
        // Coerce each argument to the field type as it is compiled (while it is on
        // top of the stack): a widening numeric value converts, like a variable
        // init or a function argument; a genuine mismatch still errors.
        args.forEachIndexed { i, a ->
            val actual = a.compile(ctx)
            val def = expected?.getOrNull(i) ?: return@forEachIndexed
            if (coerceNumeric(ctx, def, actual, (a as? IntNode)?.value, "data class '${classType.name}' constructor arg #${i + 1}") == null &&
                !actual.sameAs(def)
            ) {
                throw IllegalArgumentException(
                    "Data class '${classType.name}' constructor arg #${i + 1}: " +
                        "expected ${def.log()}, got ${actual.log()}"
                )
            }
        }
        ctx.add(Op.Frame(num = frameNum))
        ctx.add(Op.Call(args.size))
        return classType
    }

    /**
     * `new Class(args)` for a regular (non-actor, non-data) class. Mirrors the
     * fall-through path's behaviour — only generic constructor calls are arg-checked
     * (a plain [ClassType] is not [Callable]) — but emits the class frame number
     * directly so the construction is position-independent and works from any scope,
     * including an actor body. See the call site for why the var-load path can't.
     */
    private fun compileClassNew(classType: ClassType, typeArgs: List<Type>, ctx: Context): Type {
        val frameNum = classType.num
            ?: throw IllegalStateException("Class '${classType.name}' has no frame number")
        val returnType = if (typeArgs.isNotEmpty()) classType.copy(typeArgs = typeArgs) else classType
        if (typeArgs.isNotEmpty()) checkTypeArgBounds(classType.name, typeArgs)
        val argTypes = args.map { it.compile(ctx) }
        if (returnType.typeArgs.isNotEmpty()) {
            val classArgTypes = returnType.args ?: emptyList()
            val resolvedArgTypes = classArgTypes.map { returnType.resolveGeneric(it) }
            resolvedArgTypes.forEachIndexed { i, def ->
                if (i < argTypes.size) {
                    val argType = argTypes[i]
                    if (!assignableArg(def, argType)) {
                        throw Exception("Argument \"${argType.log()}\" is differ from required type ${def.log()}")
                    }
                }
            }
        }
        ctx.add(Op.Frame(num = frameNum))
        ctx.add(Op.Call(args.size))
        return returnType
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
        ctx.add(Op.ActorSpawn(classFrameNum = frameNum, className = classType.name, args = args.size))
        return ActorBoundType(classType)
    }
}
