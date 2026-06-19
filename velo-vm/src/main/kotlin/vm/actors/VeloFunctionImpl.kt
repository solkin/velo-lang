package vm.actors

import core.DataBinding
import core.VeloFunction
import core.VmType
import vm.records.FuncRecord
import java.util.concurrent.CompletableFuture

/**
 * The runtime behind [core.VeloFunction] — see the interface for the host
 * contract. Created when a Velo `func[(args) void]` argument reaches a
 * native parameter declared as `VeloFunction` (or a Kotlin function type).
 *
 * Invocation encodes the host arguments and posts them to the dispatcher of
 * the actor that owns the closure; [argTypes] is the call-site Velo
 * signature when known, used to validate arguments before they cross into
 * Velo. Each instance pins the owning actor ([ActorHandle.refCount] +
 * [Pins]), which keeps the owner's dispatcher serviceable — including the
 * main pump loop in CLI mode — until the host drops the reference.
 */
class VeloFunctionImpl internal constructor(
    internal val handle: ActorHandle,
    internal val func: FuncRecord,
    private val argTypes: List<VmType>?,
) : VeloFunction {

    init {
        handle.refCount.incrementAndGet()
        Pins.cleaner.register(this, Pins.Release(handle))
    }

    override fun post(vararg args: Any?) {
        handle.postInvoke(func, encodeArgs(args))
    }

    override fun call(vararg args: Any?): CompletableFuture<Any?> {
        val encoded = encodeArgs(args)
        if (handle.isOnOwnThread()) {
            return try {
                CompletableFuture.completedFuture(toHost(handle.invokeInline(func, encoded)))
            } catch (ex: Throwable) {
                CompletableFuture<Any?>().apply { completeExceptionally(ex) }
            }
        }
        return handle.requestInvokeAsync(func, encoded).thenApply { resp ->
            toHost(handle.unwrapResponse(resp))
        }
    }

    override fun toString(): String = "VeloFunction(actor=${handle.id}, frame=${func.frameNum})"

    // ---- host value conversion ----

    private fun encodeArgs(args: Array<out Any?>): List<ActorValue> {
        val expected = argTypes
        if (expected != null && expected.size != args.size) {
            throw IllegalArgumentException(
                "Callback expects ${expected.size} argument(s) (${expected.joinToString()}), got ${args.size}"
            )
        }
        return args.mapIndexed { i, arg ->
            encodeHost(arg, expected?.getOrNull(i), where = "argument #${i + 1}")
        }
    }

    private fun encodeHost(value: Any?, expected: VmType?, where: String): ActorValue = when (value) {
        null -> throw IllegalArgumentException("Callback $where is null — Velo has no null values")
        is Int, is Float, is Boolean, is Byte, is Char, is String -> {
            checkPrimitive(value, expected, where)
            ActorValue.Primitive(value)
        }
        is Long -> throw IllegalArgumentException("Callback $where: Velo int is 32-bit — pass Int, not Long")
        is Double -> throw IllegalArgumentException("Callback $where: Velo float is 32-bit — pass Float, not Double")
        is List<*> -> {
            val elementType = (expected as? VmType.Array)?.elementType
            ActorValue.Array(value.map { encodeHost(it, elementType, where) })
        }
        is VeloFunctionImpl -> ActorValue.Callback(value.handle, value.func)
        else -> {
            // A host value whose class is a registered data-class counterpart
            // is marshalled by value into the Velo `data class`.
            val binding = handle.ctx.nativeRegistry.dataBindingByJvmClass(value.javaClass)
                ?: throw IllegalArgumentException(
                    "Callback $where: unsupported host type ${value::class.qualifiedName}; " +
                        "use Int/Float/Boolean/Byte/String, List, a registered data class, or VeloFunction"
                )
            if (expected is VmType.Class && expected.name != binding.veloName) {
                throw IllegalArgumentException("Callback $where: expected ${expected.name}, got ${binding.veloName}")
            }
            encodeHostData(value, binding, where)
        }
    }

    /** Encode a host data-class counterpart into an [ActorValue.Data], field by field. */
    private fun encodeHostData(value: Any, binding: DataBinding, where: String): ActorValue {
        val info = handle.ctx.dataClasses.values.find { it.name == binding.veloName }
            ?: throw IllegalArgumentException("Callback $where: program has no data class '${binding.veloName}'")
        val fields = info.fields.map { field -> encodeHost(binding.read(value, field.name), field.type, where) }
        return ActorValue.Data(info.frameNum, fields)
    }

    private fun checkPrimitive(value: Any, expected: VmType?, where: String) {
        val matches = when (expected) {
            null, is VmType.Any -> true
            is VmType.Int -> value is Int
            is VmType.Float -> value is Float
            is VmType.Bool -> value is Boolean
            is VmType.Byte -> value is Byte
            is VmType.Str -> value is String
            else -> false
        }
        if (!matches) {
            throw IllegalArgumentException(
                "Callback $where: expected $expected, got ${value::class.simpleName} ($value)"
            )
        }
    }

    private fun toHost(value: ActorValue): Any? = when (value) {
        ActorValue.Void -> null
        is ActorValue.Primitive -> value.value
        is ActorValue.Array -> value.items.map { toHost(it) }
        is ActorValue.Callback -> VeloFunctionImpl(value.handle, value.func, argTypes = null)
        is ActorValue.Ref -> throw IllegalStateException(
            "actor[${value.className}] values cannot be materialised on the host side"
        )
        // Rebuild the host counterpart of a `data class` value, field by field.
        is ActorValue.Data -> {
            val info = handle.ctx.dataClasses[value.classFrameNum]
                ?: throw IllegalStateException("Unknown data class (frame ${value.classFrameNum})")
            val binding = handle.ctx.nativeRegistry.dataBindingByVeloName(info.name)
                ?: throw IllegalStateException("No host data binding registered for data class '${info.name}'")
            binding.ctorHandle.invokeWithArguments(value.fields.map { toHost(it) })
        }
    }
}
