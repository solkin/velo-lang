package vm

import core.VeloFunction
import core.VmType
import vm.actors.VeloFunctionImpl
import vm.records.RefKind
import vm.records.RefRecord
import vm.records.ValueRecord
import java.lang.reflect.Array as JArray

/**
 * The single place where values cross the Velo ⇄ JVM boundary at runtime.
 * (Load-time linking of the native pool lives in [core.NativeLinker].)
 */
object NativeBridge {

    /**
     * Convert one Velo record into the JVM argument a native method expects.
     *
     * [vmType] is the call-site Velo type (carried by the `Op.NativeCall`
     * instruction — for callbacks this is the *actual* function signature,
     * which is what arms [VeloFunction]'s argument validation); [target] is
     * the declared JVM parameter class used for final adaptation.
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

/**
 * The boxed JVM class a [VmType] materialises as when Velo values are
 * converted into typed JVM arrays (see [Record.getAs]). Boxed because
 * `Array<T>` element types are reference types.
 */
internal fun VmType.toBoxedJvm(): Class<*> = when (this) {
    is VmType.Void -> java.lang.Void::class.java
    is VmType.Any -> Any::class.java
    is VmType.Byte -> java.lang.Byte::class.java
    is VmType.Int -> java.lang.Integer::class.java
    is VmType.Float -> java.lang.Float::class.java
    is VmType.Bool -> java.lang.Boolean::class.java
    is VmType.Str -> String::class.java
    is VmType.Tuple -> Array::class.java
    is VmType.Array -> JArray.newInstance(elementType.toBoxedJvm(), 0)::class.java
    is VmType.Dict -> Map::class.java
    is VmType.Class -> throw IllegalArgumentException(
        "Arrays of native class instances ($name) cannot cross the native boundary"
    )
    is VmType.Func -> VeloFunction::class.java
    is VmType.Ptr -> throw IllegalArgumentException("Inconvertible type ptr[$derived]")
}
