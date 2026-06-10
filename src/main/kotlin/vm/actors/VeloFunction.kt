package vm.actors

import vm.VmType
import vm.records.FuncRecord
import java.util.concurrent.CompletableFuture

/**
 * A Velo function value handed to native (JVM) code — the host side of a
 * callback.
 *
 * Created automatically when a Velo `func[(args) void]` argument reaches a
 * native method parameter declared as `VeloFunction`. The host may keep it
 * for as long as it wants and invoke it from **any thread**: invocation only
 * encodes the arguments and posts them to the dispatcher of the actor that
 * owns the closure, so the body always runs on the owner's thread (for the
 * main context that is the host's chosen main/UI dispatcher).
 *
 * - [post] is fire-and-forget — the normal mode for event callbacks.
 *   Failures inside the callback are program-fatal, consistent with every
 *   other unhandled Velo runtime error.
 * - [call] additionally reports completion/failure through a
 *   [CompletableFuture]. When called from the owner's own thread (a native
 *   invoked synchronously from Velo code), the callback executes inline to
 *   avoid self-deadlock; otherwise never block the owner's thread on the
 *   returned future from itself.
 *
 * Lifetime: each instance pins the owning actor ([ActorHandle.refCount] +
 * [Pins]), which keeps the owner's dispatcher serviceable — including the
 * main pump loop in CLI mode — until the host drops the reference.
 */
class VeloFunction internal constructor(
    private val handle: ActorHandle,
    private val func: FuncRecord,
    private val argTypes: List<VmType>?,
) {

    init {
        handle.refCount.incrementAndGet()
        Pins.cleaner.register(this, Pins.Release(handle))
    }

    /** Invoke the callback on its owner's thread, without waiting. */
    fun post(vararg args: Any?) {
        handle.postInvoke(func, encodeArgs(args))
    }

    /**
     * Invoke the callback on its owner's thread and observe completion.
     * Velo callbacks return void, so the future resolves to `null` on
     * success; failures complete it exceptionally.
     */
    fun call(vararg args: Any?): CompletableFuture<Any?> {
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
        is Map<*, *> -> {
            val dictType = expected as? VmType.Dict
            ActorValue.Dict(value.entries.map { (k, v) ->
                encodeHost(k, dictType?.keyType, where) to encodeHost(v, dictType?.valueType, where)
            })
        }
        is VeloFunction -> ActorValue.Callback(value.handle, value.func)
        else -> throw IllegalArgumentException(
            "Callback $where: unsupported host type ${value::class.qualifiedName}; " +
                "use Int/Float/Boolean/Byte/String, List, Map or VeloFunction"
        )
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
        is ActorValue.Dict -> LinkedHashMap<Any?, Any?>(value.entries.size).apply {
            value.entries.forEach { (k, v) -> put(toHost(k), toHost(v)) }
        }
        is ActorValue.Callback -> VeloFunction(value.handle, value.func, argTypes = null)
        is ActorValue.Ref -> throw IllegalStateException(
            "actor[${value.className}] values cannot be materialised on the host side"
        )
    }
}
