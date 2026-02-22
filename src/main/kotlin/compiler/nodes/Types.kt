package compiler.nodes

import compiler.Context
import vm.VmType
import vm.operations.Hash

interface Type {
    fun sameAs(type: Type): Boolean
    fun default(ctx: Context)
    fun prop(name: String): Prop?
    fun log(): String
    fun vmType(): VmType
    fun name(): String
}

interface Callable : Type {
    val args: List<Type>?
}

interface Numeric : Type

interface Indexable: Type {
    fun compileIndex(ctx: Context): Type
}

interface IndexAssignable: Indexable {
    fun compileAssignment(ctx: Context)
}

object AnyType : Type {
    override fun sameAs(type: Type) = true

    override fun default(ctx: Context) {
        throw Exception("Type 'any' has no default value")
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "hash" -> AnyHashProp
            else -> null
        }
    }

    override fun log() = name()

    override fun vmType() = vm.VmType.Any

    override fun name() = "any"
}

object AnyHashProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Hash())
        return IntType
    }
}

data class GenericType(val name: String) : Type {
    override fun sameAs(type: Type): Boolean = when (type) {
        is GenericType -> name == type.name
        is AnyType -> true
        else -> false
    }

    override fun default(ctx: Context) {
        throw Exception("Generic type '$name' has no default value")
    }

    override fun prop(name: String): Prop? = null

    override fun log() = name

    override fun vmType() = VmType.Any

    override fun name() = name
}

fun inferTypeBindings(
    typeParams: List<String>,
    paramTypes: List<Type>,
    argTypes: List<Type>,
): List<Type> {
    val bindings = mutableMapOf<String, Type>()
    paramTypes.forEachIndexed { i, paramType ->
        if (i < argTypes.size) {
            collectBindings(paramType, argTypes[i], typeParams, bindings)
        }
    }
    return typeParams.map { bindings[it] ?: AnyType }
}

private fun collectBindings(
    paramType: Type,
    argType: Type,
    typeParams: List<String>,
    bindings: MutableMap<String, Type>,
) {
    when (paramType) {
        is GenericType -> {
            if (paramType.name in typeParams && paramType.name !in bindings) {
                bindings[paramType.name] = argType
            }
        }
        is ArrayType -> if (argType is ArrayType) {
            collectBindings(paramType.derived, argType.derived, typeParams, bindings)
        }
        is TupleType -> if (argType is TupleType) {
            paramType.types.forEachIndexed { i, t ->
                if (i < argType.types.size) collectBindings(t, argType.types[i], typeParams, bindings)
            }
        }
        is FuncType -> if (argType is FuncType) {
            collectBindings(paramType.derived, argType.derived, typeParams, bindings)
            paramType.args?.forEachIndexed { i, t ->
                val argArgs = argType.args
                if (argArgs != null && i < argArgs.size) {
                    collectBindings(t, argArgs[i], typeParams, bindings)
                }
            }
        }
    }
}

fun resolveGenericType(type: Type, typeParams: List<String>, typeArgs: List<Type>): Type {
    if (typeParams.isEmpty() || typeArgs.isEmpty()) return type
    return when (type) {
        is GenericType -> {
            val index = typeParams.indexOf(type.name)
            if (index in typeArgs.indices) typeArgs[index] else type
        }
        is ArrayType -> ArrayType(derived = resolveGenericType(type.derived, typeParams, typeArgs))
        is TupleType -> TupleType(types = type.types.map { resolveGenericType(it, typeParams, typeArgs) })
        is DictType -> DictType(TupleType(type.derived.types.map { resolveGenericType(it, typeParams, typeArgs) }))
        is PtrType -> PtrType(derived = resolveGenericType(type.derived, typeParams, typeArgs))
        is FuncType -> FuncType(
            derived = resolveGenericType(type.derived, typeParams, typeArgs),
            args = type.args?.map { resolveGenericType(it, typeParams, typeArgs) },
            typeParams = type.typeParams,
        )
        is ClassType -> if (type.typeArgs.isNotEmpty()) {
            type.copy(typeArgs = type.typeArgs.map { resolveGenericType(it, typeParams, typeArgs) })
        } else {
            type
        }
        else -> type
    }
}
