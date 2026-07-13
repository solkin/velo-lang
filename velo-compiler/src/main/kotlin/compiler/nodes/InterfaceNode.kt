package compiler.nodes

import core.Op

import compiler.Context

/**
 * `Self` — the receiver's own type, written in a method return position. It is
 * purely a compile-time notion (like an erased generic): at a call site it
 * resolves to the static type of the receiver — the concrete class for a
 * concrete receiver, the interface for an interface-typed one — so a fluent
 * builder method keeps the caller's type through a chain. It has no runtime
 * representation and is only ever legal as a return type (never a parameter),
 * which keeps structural satisfaction sound.
 */
object SelfType : Type {
    override fun sameAs(type: Type): Boolean = type is SelfType
    override fun default(ctx: Context): Unit = throw IllegalStateException("'Self' has no default value")
    override fun prop(name: String): Prop? = null
    override fun log() = "Self"
    override fun vmType() = core.VmType.Any
    override fun name() = "Self"
}

/**
 * A structural interface type: a set of method signatures with no state. A
 * value satisfies it when its type provides every method (matching name and
 * signature) — checked at compile time, no `implements` declaration required
 * (Go-style). Concrete classes are matched by name through [ClassTable] (a
 * [sameAs] has no [Context] to reach the class body); interfaces are matched
 * against each other by their own embedded [methods].
 *
 * [methods] is a mutable map populated *after* registration so a method
 * signature may refer to the interface itself (`func add(View child) View`):
 * the self-reference shares this very map and sees the full set once parsing
 * completes.
 */
data class InterfaceType(
    val name: String,
    val methods: MutableMap<String, FuncType> = LinkedHashMap(),
) : Type {
    /**
     * The per-compilation [ClassTable] this interface is checked against,
     * injected when the parser registers the interface. Excluded from the
     * data-class identity (name + methods): two interfaces of the same shape
     * are the same type regardless of which compilation produced them.
     */
    var classTable: ClassTable = ClassTable.EMPTY

    override fun sameAs(type: Type): Boolean = when (type) {
        is InterfaceType ->
            methods.all { (n, sig) -> type.methods[n]?.let { signatureSatisfies(sig, it, type) } == true }
        is ClassType -> {
            val provided = classTable.classMethods[type.name]
            provided != null &&
                methods.all { (n, sig) -> provided[n]?.let { signatureSatisfies(sig, it, type) } == true }
        }
        // A registered host class satisfies the interface structurally too — its
        // descriptor's method signatures are matched at the VmType level (no
        // Context here to convert them to Velo types). This is what lets a native
        // widget be used through a Velo `interface`.
        is NativeClassType -> {
            val provided = type.descriptor.methods
            methods.all { (n, sig) ->
                provided[n]?.let { nativeSignatureSatisfies(sig, it, type.descriptor.veloName) } == true
            }
        }
        else -> false
    }

    override fun default(ctx: Context): Unit =
        throw IllegalStateException("Interface '$name' has no default value; assign a concrete instance")

    override fun prop(name: String): Prop? = methods[name]?.let { InterfaceElementProp(name, it) }

    override fun log() = name

    override fun toString() = name

    override fun vmType() = core.VmType.Any

    override fun name() = name
}

/**
 * Does a class/interface method [provided] satisfy the interface requirement
 * [required]? Arity and each parameter type must match; the return type must
 * match too, except a required `Self` return is satisfied by a method that
 * returns `Self` or the satisfying type itself ([self]) — which is exactly how
 * `padding(int) Self` is fulfilled by a `Button` whose `padding` returns
 * `Button`.
 */
fun signatureSatisfies(required: FuncType, provided: FuncType, self: Type): Boolean {
    val rArgs = required.args ?: emptyList()
    val pArgs = provided.args ?: emptyList()
    if (rArgs.size != pArgs.size) return false
    for (i in rArgs.indices) if (!rArgs[i].sameAs(pArgs[i])) return false
    val rRet = required.derived
    val pRet = provided.derived
    return if (rRet is SelfType) {
        pRet is SelfType || pRet.sameAs(self) || self.sameAs(pRet)
    } else {
        rRet.sameAs(pRet)
    }
}

/**
 * Native counterpart of [signatureSatisfies], comparing at the VmType level
 * (descriptors carry no Velo [Type]s). A required `Self` return is satisfied by
 * a host method returning the host class itself; an interface/`any` parameter
 * (VmType `Any`) accepts any host class parameter, which keeps container methods
 * like `add(View)` matchable.
 */
fun nativeSignatureSatisfies(required: FuncType, provided: core.NativeMethodDescriptor, nativeName: String): Boolean {
    val rArgs = required.args ?: emptyList()
    if (rArgs.size != provided.params.size) return false
    for (i in rArgs.indices) {
        val rv = rArgs[i].vmType()
        if (rv != core.VmType.Any && rv != provided.params[i]) return false
    }
    val rRet = required.derived
    return if (rRet is SelfType) provided.returns == core.VmType.Class(nativeName)
    else rRet.vmType() == provided.returns
}

/**
 * Per-compilation table of every Velo class's method signatures (and generic
 * bounds), keyed by class name. Source of truth for structural interface
 * checks, which run inside [InterfaceType.sameAs] where no [Context] is
 * available: the parser hands each [InterfaceType] a reference to this table so
 * the check reaches class methods without a global singleton. One instance per
 * compilation unit, owned by [compiler.CompilerShared] and shared with the
 * parser; compilation is single-threaded per program.
 */
class ClassTable {
    val classMethods: MutableMap<String, Map<String, FuncType>> = HashMap()

    /** Per-class generic-parameter bounds (`class C[T: View]`), positional; null = unbounded. */
    val classBounds: MutableMap<String, List<InterfaceType?>> = HashMap()

    fun register(className: String, methods: Map<String, FuncType>) {
        require(this !== EMPTY) { "the shared empty ClassTable must not be mutated" }
        classMethods[className] = methods
    }

    fun registerBounds(className: String, bounds: List<InterfaceType?>) {
        require(this !== EMPTY) { "the shared empty ClassTable must not be mutated" }
        if (bounds.any { it != null }) classBounds[className] = bounds
    }

    companion object {
        /**
         * Shared empty table for [InterfaceType]s created outside a compilation
         * (e.g. isolated unit tests): a structural check then simply finds no
         * class methods instead of dereferencing null.
         */
        val EMPTY = ClassTable()
    }
}

/**
 * Enforce generic-parameter bounds at an instantiation site: each concrete type
 * argument must satisfy the interface bound declared for that position
 * (`class Container[T: View]` ⇒ `Container[Square]` requires `Square` to satisfy
 * `View`). Unbounded positions and non-generic classes are unaffected.
 */
fun checkTypeArgBounds(classTable: ClassTable, className: String, typeArgs: List<Type>) {
    val bounds = classTable.classBounds[className] ?: return
    typeArgs.forEachIndexed { i, arg ->
        val bound = bounds.getOrNull(i) ?: return@forEachIndexed
        if (!bound.sameAs(arg)) {
            throw IllegalStateException(
                "Type argument '${arg.log()}' does not satisfy bound '${bound.name}' " +
                    "of type parameter #${i + 1} on '$className'"
            )
        }
    }
}

/**
 * Calling a method on an interface-typed receiver. Identical in shape to a
 * concrete [ClassElementProp] call — a one-op wrapper frame entered with the
 * receiver as its parent scope — except the method slot is unknown statically,
 * so the wrapper resolves it dynamically with [Op.MethodLoad] against the
 * receiver's class method table. Arguments are checked against the interface
 * signature; a `Self` return resolves to the receiver's static type ([type]).
 */
data class InterfaceElementProp(val methodName: String, val sig: FuncType) : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        val forwardArgs = args.reversed()
        val expected = sig.args
        if (expected != null) {
            if (expected.size != forwardArgs.size) {
                throw IllegalArgumentException(
                    "Interface method '$methodName' expects ${expected.size} argument(s) but got ${forwardArgs.size}"
                )
            }
            expected.forEachIndexed { i, e ->
                if (!assignableArg(e, forwardArgs[i])) {
                    throw IllegalArgumentException(
                        "Interface method '$methodName' argument #${i + 1}: expected ${e.log()}, got ${forwardArgs[i].log()}"
                    )
                }
            }
        }
        val propCtx = ctx.discrete()
        propCtx.add(Op.MethodLoad(methodName))
        propCtx.add(Op.Call(args = args.size, reverseArgs = true))
        propCtx.add(Op.Ret)
        ctx.merge(propCtx)
        ctx.add(Op.Frame(num = propCtx.frame.num))
        // The polymorphic outer call: enters the wrapper for a Velo instance, or
        // dispatches the method by name on a native handle. Replaces the concrete
        // path's Op.Call(classParent = true).
        ctx.add(Op.InterfaceCall(method = methodName, args = args.size))
        return if (sig.derived is SelfType) type else sig.derived
    }
}

/**
 * The `interface` declaration itself. The [InterfaceType] is registered in the
 * parser as the body is read, so there is nothing to emit at compile time — an
 * interface has no runtime frame.
 */
data class InterfaceNode(val type: InterfaceType) : Node() {
    override fun compile(ctx: Context): Type {
        // Bind the interface to this compilation's class table (the single one in
        // CompilerShared) so its structural checks — InterfaceType.sameAs, which
        // runs without a Context — reach class method signatures. Done here from
        // ctx.shared rather than injected by the parser, so there is exactly one
        // table and no way to pair a parser with a different CompilerShared.
        type.classTable = ctx.shared.classTable
        return VoidType
    }
}
