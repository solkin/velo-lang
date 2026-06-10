package compiler.nodes

import compiler.Context

/**
 * Compile-time type of a value pinned to a specific actor.
 *
 * Surface syntax: `actor[T]` where `T` is some `actor class`. Constructed by
 * [TypeParser][compiler.parser.parselets.TypeParser] when it encounters
 * `actor[...]`, and by [CallNode] when an actor class is instantiated.
 *
 * `prop()` deliberately returns `null` so any direct method or field access
 * fails compilation with a clear "property not supported" error. The only
 * way to invoke methods on this type is through [AsyncNode], which performs
 * its own method resolution and emits an `ActorCall` opcode that produces a
 * [FutureType] value. The eventual result is then unwrapped by [AwaitNode].
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
 * Compile-time type of an in-flight cross-actor computation.
 *
 * Surface syntax: `future[T]`, where `T` is the value the underlying actor
 * method ultimately produces. Created by [AsyncNode] when compiling
 * `async receiver.method(args)`, and consumed by [AwaitNode] when compiling
 * `await futureExpr` — there is no other way to obtain or use a value of
 * this type.
 *
 * `prop()` returns `null` for the same reason as [ActorBoundType]: a
 * future is opaque from Velo's surface, and the only legal operation on it
 * is `await`. Ban on direct property access keeps the language honest about
 * the fact that the value is not yet computed.
 *
 * Not transferable across actor boundaries (see [isTransferable]): a
 * future's lifecycle is tied to the actor that completes it, and shipping
 * it to a third actor would create cross-thread blocking semantics we do
 * not currently model.
 */
data class FutureType(val derived: Type) : Type {

    override fun sameAs(type: Type): Boolean {
        return type is FutureType && derived.sameAs(type.derived)
    }

    override fun default(ctx: Context) {
        throw IllegalStateException("Type 'future[${derived.log()}]' has no default value; create one with `async`")
    }

    override fun prop(name: String): Prop? = null

    override fun log() = "future[${derived.log()}]"

    override fun vmType() = vm.VmType.Any

    override fun name() = "future"
}

/**
 * Whether values of this [Type] may safely cross an actor boundary.
 *
 * Two transport modes exist:
 *   - **Structural copy** (primitives, void, recursive containers of
 *     transferable elements): `StructuredClone` materialises a fresh value
 *     in the receiver's [vm.MemoryArea].
 *   - **Pinned handle** ([ActorBoundType], fully-signed void [FuncType]):
 *     the value stays in its owning actor's memory; the receiver gets a
 *     typed remote reference.
 *
 * Function values travel as callbacks — a handle to (owning actor, closure).
 * To qualify, the signature must be fully declared (`func[(int, str) void]`),
 * every argument must itself be transferable, and the return type must be
 * `void`: a callback is a notification, it executes asynchronously on its
 * owner and cannot produce a value for the invoking side. Results flow back
 * through ordinary `async`/`await` calls, which keeps
 * await-cycle deadlocks impossible by construction.
 *
 * Everything else — bare class instances, loose `func[T]` values, pointers,
 * native objects, generics, `any` — would either alias mutable state across
 * threads or has no defined wire format. The compiler rejects such types in
 * actor signatures so the violation surfaces at the point of declaration
 * rather than as an opaque runtime failure inside `await`.
 */
fun Type.isTransferable(): Boolean = when (this) {
    ByteType, IntType, FloatType, StringType, BoolType, VoidType -> true
    is ArrayType -> derived.isTransferable()
    is TupleType -> types.all { it.isTransferable() }
    is DictType -> derived.types.all { it.isTransferable() }
    is ActorBoundType -> true
    is FutureType -> false   // pinned to the actor that completes it; see [FutureType] kdoc
    is FuncType -> derived.sameAs(VoidType) && args?.all { it.isTransferable() } == true
    else -> false
}

/**
 * Throws an [IllegalStateException] tagged with [where] if [type] cannot
 * cross an actor boundary. Used to validate `actor class` signatures so the
 * error fires at declaration time, not deep inside `await`.
 */
fun requireTransferable(type: Type, where: String) {
    if (type.isTransferable()) return
    val hint = if (type is FuncType) {
        "callbacks must declare a full signature with transferable arguments and return void, " +
            "e.g. func[(int, str) void]"
    } else {
        "use a primitive, a container of transferable values, or wrap as actor[T]"
    }
    throw IllegalStateException(
        "$where has type '${type.log()}', which is not transferable across an actor boundary; $hint"
    )
}
