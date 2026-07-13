package compiler.nodes

import core.Op

import compiler.Context
import core.VmType

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

    override fun vmType() = core.VmType.Any

    override fun name() = "any"
}

object AnyHashProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.Hash)
        return IntType
    }
}

data class GenericType(val name: String, val bound: InterfaceType? = null) : Type {
    override fun sameAs(type: Type): Boolean = when (type) {
        is GenericType -> name == type.name
        is AnyType -> true
        else -> false
    }

    override fun default(ctx: Context) {
        throw Exception("Generic type '$name' has no default value")
    }

    // A bounded parameter (`[T: View]`) exposes the methods of its bound, so the
    // generic body can call them on a `T` value; the call dispatches dynamically
    // (Op.MethodLoad) exactly as on an interface-typed receiver.
    override fun prop(name: String): Prop? = bound?.prop(name)

    override fun log() = if (bound != null) "$name: ${bound.name}" else name

    override fun vmType() = VmType.Any

    override fun name() = name
}

/**
 * Is a value of [source] acceptable where [target] is expected at an argument
 * or element position? Argument sites in this compiler historically test
 * `source.sameAs(target)` (a near-symmetric relation for the original types).
 * Structural interface satisfaction is directional — only `interface.sameAs(class)`
 * holds — so this helper adds that one case without disturbing the existing
 * behaviour for every other target type.
 */
fun assignableArg(target: Type, source: Type): Boolean =
    numWidens(target, source) ||
        source.sameAs(target) ||
        (target is InterfaceType && target.sameAs(source))

/** Widening order of the primitive numeric types; `null` for anything else. */
fun numericRank(t: Type): Int? = when (t) {
    ByteType -> 0
    IntType -> 1
    LongType -> 2
    FloatType -> 3
    else -> null
}

/** Does a value of [source] widen losslessly to [target] (byte -> int -> long -> float)? */
fun numWidens(target: Type, source: Type): Boolean {
    val tr = numericRank(target) ?: return false
    val sr = numericRank(source) ?: return false
    return sr <= tr
}

/**
 * Coerce the value on top of the stack from [source] to [target] where a value
 * of [target] is expected (variable init, assignment, return, element, arg).
 *
 * Widening (byte -> int -> float) is inserted automatically; an integer literal
 * ([intLiteral] non-null) additionally adapts *down* to a range-checked byte, so
 * `byte b = 65` and `float f = 5` both work. Any other narrowing is rejected —
 * the caller must convert explicitly with `.int()` / `.byte()`.
 *
 * Returns [target] when it handled a numeric coercion, or `null` when the two
 * types are not both numeric (the caller then applies its own type check).
 */
fun coerceNumeric(ctx: Context, target: Type, source: Type, intLiteral: Int?, what: String): Type? {
    val tr = numericRank(target) ?: return null
    val sr = numericRank(source) ?: return null
    if (sr == tr) return target
    if (sr < tr) {                                  // widening (byte -> int -> long -> float)
        when (target) {
            LongType -> ctx.add(Op.NumConv(source.vmType(), VmType.Long))
            FloatType -> ctx.add(Op.NumConv(source.vmType(), VmType.Float))
            else -> {}                                     // byte -> int flows as int already
        }
        return target
    }
    if (intLiteral != null && target === ByteType) { // int literal -> byte
        if (intLiteral !in -128..255) {
            throw IllegalArgumentException("Byte literal $intLiteral is out of range for $what (-128..255)")
        }
        ctx.add(Op.NumConv(VmType.Int, VmType.Byte))
        return target
    }
    throw IllegalArgumentException(
        "Cannot assign ${source.log()} to $what of type ${target.log()} without losing data. " +
            "Convert explicitly with .${target.name()}() (e.g. value.${target.name()}())."
    )
}

/** `.int()`/`.float()`/`.byte()` no-op conversion — keeps the receiver's type. */
object NumIdentityProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type = type
}

/** `x.float()` — convert any numeric receiver to float. */
object ToFloatProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.NumConv(type.vmType(), VmType.Float))
        return FloatType
    }
}

/** `x.int()` — convert any numeric receiver to int (float/long truncate toward zero). */
object ToIntProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.NumConv(type.vmType(), VmType.Int))
        return IntType
    }
}

/** `x.long()` — convert any numeric receiver to a 64-bit long. */
object ToLongProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.NumConv(type.vmType(), VmType.Long))
        return LongType
    }
}

/** `x.byte()` — convert any numeric receiver to a byte (low 8 bits). */
object ToByteProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.NumConv(type.vmType(), VmType.Byte))
        return ByteType
    }
}

/** `byte.int()` — a byte already flows as its int value, so no conversion op. */
object ByteToIntProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type = IntType
}

/** `x.str()` — decimal string of any numeric receiver (byte/int/long/float). */
object NumStrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.NumStr)
        return StringType
    }
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
