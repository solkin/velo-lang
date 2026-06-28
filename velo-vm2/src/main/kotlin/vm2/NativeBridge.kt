package vm2

import core.BoundNative
import core.NativeRef
import core.VeloFunction
import core.VmType

/**
 * The single Velo ⇄ JVM conversion point for `Op.NativeCall`.
 *
 * Velo primitives already map onto Kotlin primitives, so conversion is mostly
 * coercion (`Number` widening) plus marshalling arrays to/from JVM arrays and
 * `List`s. A function argument is wrapped as a host [VeloFunction] (or, for a
 * Kotlin function-type parameter, a plain lambda) that routes invocations back
 * to the owning actor's dispatcher. Opaque native handles pass straight through.
 */
class NativeBridge {

    /**
     * Invoke a linked native entry. [args] are popped in declaration order;
     * [receiver] is the instance for method entries. [owner] is the actor
     * making the call (the default owner of any function argument), and
     * [support] supplies callback wrapping. Returns the converted result, or
     * [Interpreter.NO_VALUE] for `void` methods.
     */
    fun call(bound: BoundNative, receiver: Any?, args: List<Any?>, owner: ActorRef, support: NativeSupport): Any? {
        val invokeArgs = ArrayList<Any?>(args.size + 1)
        if (!bound.isConstructor) invokeArgs.add(receiver)
        args.forEachIndexed { i, a ->
            invokeArgs.add(toJvm(a, bound.jvmParams.getOrNull(i), owner, support))
        }
        val result = bound.handle.invokeWithArguments(invokeArgs)
        if (bound.ref.kind == NativeRef.Kind.METHOD && bound.ref.returns == VmType.Void) {
            return Interpreter.NO_VALUE
        }
        // Routed through the scheduler so a returned host `data class` is
        // re-materialised by value into a Velo instance; primitives fall back
        // to [hostToVelo].
        return support.hostToVelo(result)
    }

    private fun toJvm(value: Any?, target: Class<*>?, owner: ActorRef, support: NativeSupport): Any? {
        if (target == null) return value
        if (target == VeloFunction::class.java) {
            return value?.let { support.makeCallback(it, owner) }
        }
        if (isKotlinFunction(target)) {
            return value?.let { kotlinFunction(functionArity(target), support.makeCallback(it, owner)) }
        }
        // A Velo `data class` instance crossing to a native is marshalled by value.
        if (value is Instance) return support.veloToHost(value)
        if (value == null) return null
        return when (target) {
            Integer.TYPE, java.lang.Integer::class.java -> (value as Number).toInt()
            java.lang.Float.TYPE, java.lang.Float::class.java -> (value as Number).toFloat()
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> value as Boolean
            java.lang.Byte.TYPE, java.lang.Byte::class.java -> (value as Number).toByte()
            String::class.java -> value as String
            else -> when {
                target.isArray && value is VArray -> toJvmArray(value, target.componentType)
                List::class.java.isAssignableFrom(target) && value is VArray -> value.data.toMutableList()
                else -> value
            }
        }
    }

    private fun toJvmArray(array: VArray, component: Class<*>): Any = when (component) {
        Integer.TYPE -> IntArray(array.size) { (array.data[it] as Number).toInt() }
        java.lang.Byte.TYPE -> ByteArray(array.size) { (array.data[it] as Number).toByte() }
        java.lang.Float.TYPE -> FloatArray(array.size) { (array.data[it] as Number).toFloat() }
        java.lang.Boolean.TYPE -> BooleanArray(array.size) { array.data[it] as Boolean }
        else -> {
            val out = java.lang.reflect.Array.newInstance(component, array.size)
            for (i in 0 until array.size) java.lang.reflect.Array.set(out, i, array.data[i])
            out
        }
    }

    /** Host → Velo for native return values and callback arguments. */
    fun hostToVelo(value: Any?): Any? = toVelo(value)

    private fun toVelo(value: Any?): Any? = when (value) {
        null, Unit -> Interpreter.NO_VALUE
        is IntArray -> VArray(Array(value.size) { value[it] })
        is ByteArray -> VArray(Array(value.size) { value[it] })
        is FloatArray -> VArray(Array(value.size) { value[it] })
        is BooleanArray -> VArray(Array(value.size) { value[it] })
        is Array<*> -> VArray(Array(value.size) { value[it] })
        is List<*> -> VArray(Array(value.size) { value[it] })
        else -> value
    }

    /** Velo → Host (loose, no target type) for callback return values. */
    fun veloToHost(value: Any?): Any? = when (value) {
        Interpreter.NO_VALUE -> null
        is VArray -> Array(value.size) { value.data[it] }
        else -> value
    }

    // ---- Kotlin function-type callbacks ----

    private fun isKotlinFunction(c: Class<*>): Boolean = c.name.startsWith("kotlin.jvm.functions.Function")

    private fun functionArity(c: Class<*>): Int =
        c.simpleName.removePrefix("Function").toIntOrNull()
            ?: error("Unsupported callback type ${c.name}")

    private fun kotlinFunction(arity: Int, vf: VeloFunction): Any {
        val f0: () -> Unit = { vf.post() }
        val f1: (Any?) -> Unit = { a -> vf.post(a) }
        val f2: (Any?, Any?) -> Unit = { a, b -> vf.post(a, b) }
        val f3: (Any?, Any?, Any?) -> Unit = { a, b, c -> vf.post(a, b, c) }
        val f4: (Any?, Any?, Any?, Any?) -> Unit = { a, b, c, d -> vf.post(a, b, c, d) }
        return when (arity) {
            0 -> f0
            1 -> f1
            2 -> f2
            3 -> f3
            4 -> f4
            else -> error("Unsupported callback arity $arity (max 4)")
        }
    }
}
