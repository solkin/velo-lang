package compiler.nodes

import core.DataClassInfo
import core.DataField
import core.Op

import compiler.Context

data class ClassNode(
    val name: String,
    val isActor: Boolean = false,
    val isData: Boolean = false,
    val typeParams: List<String> = emptyList(),
    val defs: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (isActor && typeParams.isNotEmpty()) {
            throw IllegalStateException("Generic 'actor class' is not supported (class '$name')")
        }
        if (isData && typeParams.isNotEmpty()) {
            throw IllegalStateException("Generic 'data class' is not supported (class '$name')")
        }
        // Reserve the class name index before extending, so class body vars don't shadow it
        val nameVar = ctx.def(name, VoidType)

        val classOps = ctx.extend()
        val argTypes = ArrayList<Type>()

        val classType: Type = ClassType(
            name = name,
            isActor = isActor,
            isData = isData,
            typeParams = typeParams,
            num = classOps.frame.num,
            parent = classOps,
            args = argTypes,
        )
        ctx.retype(name, classType)

        // Insert class frame pointer into stack
        ctx.add(Op.Frame(num = classOps.frame.num))
        ctx.add(Op.Store(index = nameVar.index))

        // Compile class body. A `data class` is an immutable value type: its
        // fields (the constructor parameters) cannot be reassigned, so their
        // var slots are marked immutable.
        val args = defs.reversed().map { def ->
            val v = classOps.def(def.name, def.type, immutable = isData)
            classOps.add(Op.Store(v.index))
            v
        }.reversed()
        argTypes += args.map { it.type }

        body.compile(ctx = classOps)
        classOps.add(Op.Instance)
        classOps.add(Op.Ret)

        if (isActor) {
            validateActorSignatures(classOps)
        }
        if (isData) {
            validateDataClass(classOps)
            ctx.shared.dataClasses.add(
                DataClassInfo(
                    frameNum = classOps.frame.num,
                    name = name,
                    fields = defs.mapIndexed { i, def ->
                        DataField(name = def.name, index = args[i].index, type = def.type.vmType())
                    },
                )
            )
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

    /**
     * Enforce the value-type contract of a `data class`:
     *
     *   - state is exactly the constructor parameters — the body may only
     *     declare methods, never additional fields;
     *   - every field is itself transferable, so a data class is always safe
     *     to copy across actor and native boundaries (see [isTransferable]).
     *
     * Immutability of the fields is handled separately by marking their var
     * slots immutable at definition time.
     */
    private fun validateDataClass(classCtx: Context) {
        defs.forEach { def ->
            requireTransferable(def.type, "Data class '$name' field '${def.name}'")
        }
        val fieldNames = defs.map { it.name }.toSet()
        classCtx.frame.vars.forEach { (memberName, v) ->
            if (memberName in fieldNames) return@forEach
            if (v.type !is FuncType) {
                throw IllegalStateException(
                    "Data class '$name' may only declare methods in its body; " +
                        "member '$memberName' is a field — make it a constructor parameter"
                )
            }
        }
    }
}

data class ClassType(
    val name: String,
    val isActor: Boolean = false,
    val isData: Boolean = false,
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
        ctx.add(Op.Frame(num = 0))
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

    override fun vmType() = core.VmType.Class(name)

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
        propCtx.add(Op.Load(v.index))
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
            propCtx.add(Op.Call(args = -args.size))
            resultType = funcType.derived
        }
        propCtx.add(Op.Ret)
        ctx.merge(propCtx)
        ctx.add(Op.Frame(num = propCtx.frame.num))
        ctx.add(Op.Call(args = args.size, classParent = true))
        var resolved = instanceType.resolveGeneric(resultType)
        if (methodTypeBindings.isNotEmpty() && funcType != null) {
            resolved = resolveGenericType(resolved, funcType.typeParams, methodTypeBindings)
        }
        return resolved
    }
}
