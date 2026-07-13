package compiler.nodes

import core.Op

import compiler.Context
import core.NativeClassDescriptor
import core.NativeMethodDescriptor
import core.VmType

/**
 * Compile-time type of a registered host (JVM) class, synthesized from the
 * registry's [NativeClassDescriptor] — there is no `native class`
 * declaration in Velo source any more; registering the class on the runtime
 * is the single source of truth.
 *
 * Instances are opaque native handles at runtime (`RefRecord` of NATIVE
 * kind): no class frame, no Velo-visible fields. The only operations are
 * method calls, resolved here against the descriptor and compiled into
 * `NativeCall` ops over the program's native pool.
 */
data class NativeClassType(val descriptor: NativeClassDescriptor) : Type {

    override fun sameAs(type: Type): Boolean =
        type is NativeClassType && type.descriptor.veloName == descriptor.veloName

    override fun default(ctx: Context) {
        throw IllegalStateException(
            "Native class '${descriptor.veloName}' has no default value; instantiate it with `new`"
        )
    }

    override fun prop(name: String): Prop? =
        descriptor.methods[name]?.let { NativeMethodProp(descriptor, it) }

    override fun log() = descriptor.veloName

    override fun vmType() = VmType.Class(descriptor.veloName)

    override fun name() = descriptor.veloName
}

/**
 * Method dispatch on a native class: validates the call against the
 * descriptor's Velo-visible signature and emits a single [NativeCall].
 *
 * Receives `args` in the reversed order [PropNode] compiled them (last
 * argument first) — same contract as [ClassElementProp].
 */
class NativeMethodProp(
    private val owner: NativeClassDescriptor,
    private val method: NativeMethodDescriptor,
) : Prop {

    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        val expected = method.params.map { vmTypeToType(it, ctx) }
        val actual = args.reversed()
        if (expected.size != actual.size) {
            throw IllegalArgumentException(
                "Native method '${owner.veloName}.${method.name}' expects ${expected.size} args, got ${actual.size}"
            )
        }
        expected.forEachIndexed { i, exp ->
            if (!assignableArg(exp, actual[i])) {
                throw IllegalArgumentException(
                    "Native method '${owner.veloName}.${method.name}' arg #${i + 1}: " +
                        "expected ${exp.log()}, got ${actual[i].log()}"
                )
            }
        }
        val index = ctx.shared.intern(owner.methodRef(method.name))
        ctx.add(Op.NativeCall(poolIndex = index, args = callSiteVmTypes(method.params, actual)))
        return vmTypeToType(method.returns, ctx)
    }
}

/**
 * The per-argument Velo types a `NativeCall` op carries for runtime
 * conversion. Defaults to the declared parameter types; for callback
 * parameters declared loosely (a raw `VeloFunction` — `args == null`) the
 * *actual* function signature from the call site is substituted, so the
 * host-side wrapper can still validate invocation arguments.
 */
internal fun callSiteVmTypes(declared: List<VmType>, actual: List<Type>): List<VmType> =
    declared.mapIndexed { i, decl ->
        if (decl is VmType.Func && decl.args == null) actual[i].vmType() else decl
    }

/**
 * Materialise a descriptor's [VmType] as a compiler [Type] for signature
 * checking. Class references resolve through the compilation registry —
 * a native method may accept or return other registered classes.
 */
internal fun vmTypeToType(vmType: VmType, ctx: Context): Type = when (vmType) {
    is VmType.Void -> VoidType
    is VmType.Any -> AnyType
    is VmType.Byte -> ByteType
    is VmType.Int -> IntType
    is VmType.Long -> LongType
    is VmType.Float -> FloatType
    is VmType.Str -> StringType
    is VmType.Bool -> BoolType
    is VmType.Array -> ArrayType(vmTypeToType(vmType.elementType, ctx))
    is VmType.Tuple -> TupleType(vmType.elementTypes.map { vmTypeToType(it, ctx) })
    is VmType.Func -> FuncType(
        derived = vmType.ret?.let { vmTypeToType(it, ctx) } ?: VoidType,
        args = vmType.args?.map { vmTypeToType(it, ctx) },
    )
    is VmType.Class -> {
        // A `VmType.Class` in a native signature is either a Velo `data class`
        // (marshalled by value — resolve to its declared type) or a registered
        // native class (an opaque handle).
        val veloData = (ctx.opt(vmType.name)?.type as? ClassType)?.takeIf { it.isData }
        veloData
            ?: ctx.shared.descriptor(vmType.name)?.let { NativeClassType(it) }
            ?: throw IllegalArgumentException(
                "Class '${vmType.name}' is neither a data class nor a registered native class"
            )
    }
    is VmType.Ptr -> PtrType(derived = vmTypeToType(vmType.derived, ctx))
}
