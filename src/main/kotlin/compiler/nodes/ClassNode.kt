package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.Frame
import vm.operations.Load
import vm.operations.Instance
import vm.operations.Ret
import vm.operations.Store

data class ClassNode(
    val name: String,
    val isActor: Boolean = false,
    val typeParams: List<String> = emptyList(),
    val defs: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (isActor && typeParams.isNotEmpty()) {
            throw IllegalStateException("Generic 'actor class' is not supported (class '$name')")
        }
        // Reserve the class name index before extending, so class body vars don't shadow it
        val nameVar = ctx.def(name, VoidType)

        val classOps = ctx.extend()
        val argTypes = ArrayList<Type>()

        val classType: Type = ClassType(
            name = name,
            isActor = isActor,
            typeParams = typeParams,
            num = classOps.frame.num,
            parent = classOps,
            args = argTypes,
        )
        ctx.retype(name, classType)

        // Insert class frame pointer into stack
        ctx.add(Frame(num = classOps.frame.num))
        ctx.add(Store(index = nameVar.index))

        // Compile class body
        val args = defs.reversed().map { def ->
            val v = classOps.def(def.name, def.type)
            classOps.add(Store(v.index))
            v
        }.reversed()
        argTypes += args.map { it.type }

        body.compile(ctx = classOps)
        classOps.add(Instance())
        classOps.add(Ret())

        if (isActor) {
            validateActorSignatures(classOps)
        }

        // Add class operations to the real context
        ctx.merge(classOps)

        return VoidType
    }

    /**
     * Enforce that every value reaching or leaving this actor's surface is
     * transferable (see [isTransferable]).
     *
     * Velo classes are mutable; sharing them by reference across actor
     * threads would race, and structural-copy semantics for unrestricted
     * classes would silently turn aliased mutations into snapshots. The
     * compiler closes the gap at the point of declaration so the user sees
     * a clear error here rather than an opaque marshalling failure inside
     * `await`.
     */
    private fun validateActorSignatures(classCtx: Context) {
        defs.forEach { def ->
            requireTransferable(def.type, "Actor '$name' constructor parameter '${def.name}'")
        }
        classCtx.frame.vars.forEach { (memberName, v) ->
            val funcType = v.type as? FuncType ?: return@forEach
            funcType.args?.forEachIndexed { i, argType ->
                requireTransferable(argType, "Actor method '$name.$memberName' arg #${i + 1}")
            }
            requireTransferable(funcType.derived, "Actor method '$name.$memberName' return type")
        }
    }
}

data class ClassType(
    val name: String,
    val isActor: Boolean = false,
    val typeParams: List<String> = emptyList(),
    val typeArgs: List<Type> = emptyList(),
    val num: Int? = null,
    val parent: Context? = null,
    val args: List<Type>? = null,
) : Type {
    override fun sameAs(type: Type): Boolean {
        if (type !is ClassType || type.name != name) return false
        if (typeArgs.isEmpty() || type.typeArgs.isEmpty()) return true
        if (typeArgs.size != type.typeArgs.size) return false
        return typeArgs.zip(type.typeArgs).all { (a, b) -> a.sameAs(b) }
    }

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop {
        return ClassElementProp(name)
    }

    override fun log() = if (typeArgs.isNotEmpty()) {
        "$name[${typeArgs.joinToString(", ") { it.log() }}]"
    } else {
        name
    }

    override fun toString() = log()

    override fun vmType() = vm.VmType.Class(name)

    override fun name() = name

    fun resolveGeneric(type: Type): Type = resolveGenericType(type, typeParams, typeArgs)
}

data class ClassElementProp(val name: String) : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        val instanceType = type as? ClassType ?: throw IllegalArgumentException("Class operation on non-class type $type")
        val defType = ctx.get(instanceType.name).type as? ClassType
            ?: throw IllegalArgumentException("Class $instanceType is not defined in this scope")
        defType.parent ?: throw IllegalStateException("Class prop parent context is not defined")
        val v = defType.parent.frame.vars[name] ?: throw IllegalArgumentException("Class has no property $name")
        val propCtx = ctx.discrete(parent = defType.parent)
        propCtx.add(Load(v.index))
        var resultType = v.type
        val funcType = v.type as? FuncType
        var methodTypeBindings: List<Type> = emptyList()
        if (funcType != null) {
            val funcArgTypes = funcType.args
            if (funcArgTypes != null) {
                val forwardArgs = args.reversed()
                var resolvedParamTypes = funcArgTypes.map { instanceType.resolveGeneric(it) }
                if (funcType.typeParams.isNotEmpty()) {
                    methodTypeBindings = inferTypeBindings(funcType.typeParams, resolvedParamTypes, forwardArgs)
                    resolvedParamTypes = resolvedParamTypes.map { resolveGenericType(it, funcType.typeParams, methodTypeBindings) }
                }
                if (resolvedParamTypes.size != forwardArgs.size) {
                    throw Exception("Method '$name' args count ${forwardArgs.size} differs from required ${resolvedParamTypes.size}")
                }
                resolvedParamTypes.forEachIndexed { i, expectedType ->
                    if (!expectedType.sameAs(forwardArgs[i])) {
                        throw Exception("Method '$name' argument type ${forwardArgs[i].log()} differs from required ${expectedType.log()}")
                    }
                }
            }
            propCtx.add(Call(args = -args.size))
            resultType = funcType.derived
        }
        propCtx.add(Ret())
        ctx.merge(propCtx)
        ctx.add(Frame(num = propCtx.frame.num))
        ctx.add(Call(args = args.size, classParent = true))
        var resolved = instanceType.resolveGeneric(resultType)
        if (methodTypeBindings.isNotEmpty() && funcType != null) {
            resolved = resolveGenericType(resolved, funcType.typeParams, methodTypeBindings)
        }
        return resolved
    }
}
