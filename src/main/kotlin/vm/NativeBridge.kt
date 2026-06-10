package vm

import vm.actors.VeloFunction
import vm.records.RefKind
import vm.records.RefRecord
import vm.records.ValueRecord
import java.lang.invoke.MethodHandle

/**
 * One linked native pool entry: the serialized [NativeRef] joined with the
 * registry's resolved [MethodHandle] and JVM parameter classes. Produced by
 * [NativeLinker.link] before the program runs; indexed directly by the
 * `NativeCall` opcode.
 */
class BoundNative(
    val ref: NativeRef,
    val handle: MethodHandle,
    val jvmParams: List<Class<*>>,
) {
    val isConstructor: Boolean get() = ref.kind == NativeRef.Kind.CONSTRUCTOR
}

/**
 * Resolves a program's native pool against a [NativeRegistry] — the
 * load-time "link" step.
 *
 * All entries are checked before execution starts, and *every* problem is
 * reported in one [NativeMappingException]: for an embedding host this is
 * the complete diff between what the script expects and what the host
 * provides, instead of a sequence of mid-run failures.
 */
object NativeLinker {

    fun link(pool: List<NativeRef>, registry: NativeRegistry): Array<BoundNative> {
        val problems = mutableListOf<String>()
        val bound = pool.map { ref ->
            val descriptor = try {
                registry.descriptor(ref.className)
            } catch (ex: NativeMappingException) {
                problems += ex.problems
                null
            }
            if (descriptor == null) {
                if (!problems.any { it.contains("'${ref.className}'") }) {
                    problems += "native class '${ref.className}' is not registered in this runtime"
                }
                return@map null
            }
            when (ref.kind) {
                NativeRef.Kind.CONSTRUCTOR -> {
                    if (descriptor.ctorParams != ref.params) {
                        problems += "constructor of '${ref.className}': program expects " +
                            "(${ref.params.joinToString()}), host provides (${descriptor.ctorParams.joinToString()})"
                        null
                    } else {
                        BoundNative(ref, descriptor.ctorHandle, descriptor.ctorJvmParams)
                    }
                }
                NativeRef.Kind.METHOD -> {
                    val method = descriptor.methods[ref.methodName]
                    when {
                        method == null -> {
                            problems += "'${ref.className}.${ref.methodName}' is missing on the host class " +
                                descriptor.jvmClass.name
                            null
                        }
                        method.params != ref.params || method.returns != ref.returns -> {
                            problems += "'${ref.className}.${ref.methodName}': program expects " +
                                "(${ref.params.joinToString()}): ${ref.returns}, host provides " +
                                "(${method.params.joinToString()}): ${method.returns}"
                            null
                        }
                        else -> BoundNative(ref, method.handle, method.jvmParams)
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw NativeMappingException(problems)
        }
        return bound.map { it!! }.toTypedArray()
    }
}

/**
 * The single place where values cross the Velo ⇄ JVM boundary.
 */
object NativeBridge {

    /**
     * Convert one Velo record into the JVM argument a native method expects.
     *
     * [vmType] is the call-site Velo type (carried by the `NativeCall` op —
     * for callbacks this is the *actual* function signature, which is what
     * arms [VeloFunction]'s argument validation); [target] is the declared
     * JVM parameter class used for final adaptation.
     */
    fun toJvm(record: Record, vmType: VmType, target: Class<*>, ctx: VMContext): Any = when (vmType) {
        is VmType.Func -> adaptCallback(record.getAs(vmType, ctx) as VeloFunction, target)
        is VmType.Class -> {
            val instance = (record as? RefRecord)?.takeIf { it.kind == RefKind.NATIVE }?.get<Any>(ctx)
                ?: throw IllegalArgumentException(
                    "Expected a native ${vmType.name} instance, got ${record::class.simpleName}"
                )
            require(target.isInstance(instance)) {
                "Native instance of ${instance::class.java.name} is not assignable to ${target.name}"
            }
            instance
        }
        else -> adapt(record.getAs(vmType, ctx), target)
    }

    /** Velo containers materialise as JVM arrays; adapt when the host wants a List. */
    private fun adapt(value: Any, target: Class<*>): Any = when {
        target.isInstance(value) || target.isPrimitive -> value
        List::class.java.isAssignableFrom(target) && value is Array<*> -> value.toList()
        else -> value
    }

    /**
     * Hand a Velo callback to the host in the declared shape: the explicit
     * [VeloFunction] handle, or a plain Kotlin function value
     * (`(Int) -> Unit`) that posts through it.
     */
    private fun adaptCallback(function: VeloFunction, target: Class<*>): Any {
        if (target == VeloFunction::class.java || target == Any::class.java) return function
        return when (target.name) {
            "kotlin.jvm.functions.Function0" -> object : Function0<Unit> {
                override fun invoke() = function.post()
            }
            "kotlin.jvm.functions.Function1" -> object : Function1<Any?, Unit> {
                override fun invoke(p1: Any?) = function.post(p1)
            }
            "kotlin.jvm.functions.Function2" -> object : Function2<Any?, Any?, Unit> {
                override fun invoke(p1: Any?, p2: Any?) = function.post(p1, p2)
            }
            "kotlin.jvm.functions.Function3" -> object : Function3<Any?, Any?, Any?, Unit> {
                override fun invoke(p1: Any?, p2: Any?, p3: Any?) = function.post(p1, p2, p3)
            }
            "kotlin.jvm.functions.Function4" -> object : Function4<Any?, Any?, Any?, Any?, Unit> {
                override fun invoke(p1: Any?, p2: Any?, p3: Any?, p4: Any?) = function.post(p1, p2, p3, p4)
            }
            else -> throw IllegalArgumentException(
                "Callback parameter type ${target.name} is not supported; " +
                    "declare it as VeloFunction or a Kotlin function type up to 4 arguments"
            )
        }
    }

    /**
     * Wrap a native (JVM) result into a Velo Record, allocating containers
     * in the calling context's memory. Returns `null` for void results
     * (Unit / null) — the caller pushes nothing.
     */
    fun toVelo(value: Any?, ctx: VMContext): Record? {
        return when (value) {
            null, Unit -> null
            is Boolean, is Byte, is Int, is Long, is Float, is Double, is Char, is String ->
                ValueRecord(value)
            is Map<*, *> -> {
                val veloMap = HashMap<Record, Record>()
                for ((k, v) in value) {
                    if (k != null && v != null) {
                        veloMap[toVelo(k, ctx)!!] = toVelo(v, ctx)!!
                    }
                }
                RefRecord.dict(veloMap, ctx)
            }
            else -> asIterable(value)?.let { wrapIterable(it, ctx) }
                ?: RefRecord.native(value, ctx)
        }
    }

    private fun asIterable(value: Any): Iterable<*>? = when (value) {
        is Array<*> -> value.asIterable()
        is ByteArray -> value.asIterable()
        is IntArray -> value.asIterable()
        is FloatArray -> value.asIterable()
        is BooleanArray -> value.asIterable()
        is List<*> -> value
        else -> null
    }

    private fun wrapIterable(items: Iterable<*>, ctx: VMContext): Record {
        val veloArray = items.map { element ->
            toVelo(element, ctx) ?: ValueRecord(Unit)
        }.toTypedArray()
        return RefRecord.array(veloArray, ctx)
    }
}
