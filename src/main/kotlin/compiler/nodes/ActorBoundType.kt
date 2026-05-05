package compiler.nodes

import compiler.Context

/**
 * Compile-time type of a value pinned to a specific actor.
 *
 * Surface syntax: `actor[T]` where `T` is some `actor class`. Constructed by
 * [TypeParser][compiler.parser.parselets.TypeParser] when it encounters
 * `actor[...]`, by [ClassNode] / [CallNode] when an actor class is
 * instantiated, and by [AwaitNode] when an actor method returns another
 * actor-bound class.
 *
 * `prop()` deliberately returns `null` so any direct method or field access
 * fails compilation with a clear "property not supported" error. The only
 * way to invoke methods on this type is through [AwaitNode], which performs
 * its own resolution and emits an `ActorAwaitCall` opcode. This is the
 * compile-time guarantee that *every* cross-actor call is preceded by
 * `await`.
 */
data class ActorBoundType(val derived: ClassType) : Type {

    init {
        require(derived.isActor) {
            "ActorBoundType can only wrap actor classes, but '${derived.name}' is not an actor"
        }
    }

    override fun sameAs(type: Type): Boolean {
        return type is ActorBoundType && derived.sameAs(type.derived)
    }

    override fun default(ctx: Context) {
        throw IllegalStateException("Type 'actor[${derived.name}]' has no default value; spawn it with `new ${derived.name}()`")
    }

    override fun prop(name: String): Prop? = null

    override fun log() = "actor[${derived.log()}]"

    override fun vmType() = vm.VmType.Any

    override fun name() = "actor"
}

/**
 * Whether values of this [Type] may safely cross an actor boundary.
 *
 * Two transport modes exist:
 *   - **Structural copy** (primitives, void, recursive containers of
 *     transferable elements): `StructuredClone` materialises a fresh value
 *     in the receiver's [vm.MemoryArea].
 *   - **Pinned handle** ([ActorBoundType]): the value stays in its owning
 *     actor's memory; the receiver gets a typed remote reference.
 *
 * Everything else — bare class instances, function values, pointers, native
 * objects, generics, `any` — would either alias mutable state across threads
 * or has no defined wire format. The compiler rejects such types in actor
 * signatures so the violation surfaces at the point of declaration rather
 * than as an opaque runtime failure inside `await`.
 */
fun Type.isTransferable(): Boolean = when (this) {
    ByteType, IntType, FloatType, StringType, BoolType, VoidType -> true
    is ArrayType -> derived.isTransferable()
    is TupleType -> types.all { it.isTransferable() }
    is DictType -> derived.types.all { it.isTransferable() }
    is ActorBoundType -> true
    else -> false
}

/**
 * Throws an [IllegalStateException] tagged with [where] if [type] cannot
 * cross an actor boundary. Used to validate `actor class` signatures so the
 * error fires at declaration time, not deep inside `await`.
 */
fun requireTransferable(type: Type, where: String) {
    if (!type.isTransferable()) {
        throw IllegalStateException(
            "$where has type '${type.log()}', which is not transferable across an actor boundary; " +
                "use a primitive, a container of transferable values, or wrap as actor[T]"
        )
    }
}
