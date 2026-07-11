package vm3

import core.Op
import core.SerializedFrame

internal object Uninitialized
internal object NullPointerValue

// ---------------------------------------------------------------------------
// Tagged value representation.
//
// Primitives never leave the interpreter boxed: a value lives as (tag, bits)
// where `bits` is a raw Long and `tag` selects how to read it. Only genuine
// reference types (String, VArray, InstanceValue, …) occupy the parallel
// `ref` slot. TAG_UNINIT is 0 so a fresh ByteArray reads as "uninitialized"
// for free — no fill() pass on every scope/frame.
// ---------------------------------------------------------------------------
internal const val TAG_UNINIT: Byte = 0
internal const val TAG_REF: Byte = 1
internal const val TAG_INT: Byte = 2
internal const val TAG_LONG: Byte = 3
internal const val TAG_FLOAT: Byte = 4
internal const val TAG_BOOL: Byte = 5
internal const val TAG_BYTE: Byte = 6

internal fun tagOf(value: Any?): Byte = when (value) {
    is Int -> TAG_INT
    is Long -> TAG_LONG
    is Float -> TAG_FLOAT
    is Boolean -> TAG_BOOL
    is Byte -> TAG_BYTE
    else -> TAG_REF
}

internal fun primBitsOf(value: Any?): Long = when (value) {
    is Int -> value.toLong()
    is Long -> value
    is Float -> value.toRawBits().toLong()
    is Boolean -> if (value) 1L else 0L
    is Byte -> value.toLong()
    else -> 0L
}

/** Re-box a slot into a JVM object. Used only on cold boundaries (native, actor, pointers). */
internal fun boxSlot(tag: Byte, bits: Long, ref: Any?): Any? = when (tag) {
    TAG_REF -> ref
    TAG_INT -> bits.toInt()
    TAG_LONG -> bits
    TAG_FLOAT -> Float.fromBits(bits.toInt())
    TAG_BOOL -> bits != 0L
    TAG_BYTE -> bits.toByte()
    else -> throw VeloError("Read of an uninitialized value")
}

internal class FrameSpec(frame: SerializedFrame) {
    @JvmField val num: Int = frame.num
    @JvmField val ops: Array<Op> = frame.ops.toTypedArray()
    @JvmField val vars: IntArray = frame.vars.toIntArray()

    /** Per-op keys for ScopeEnter, precomputed once so a loop body's scope
     *  no longer allocates a fresh IntArray every iteration. */
    @JvmField val scopeKeys: Array<IntArray?> = arrayOfNulls<IntArray>(ops.size).also { arr ->
        ops.forEachIndexed { i, op -> if (op is Op.ScopeEnter) arr[i] = IntArray(op.count) { op.base + it } }
    }

    /**
     * True if this frame's environment can outlive the call — it captures itself
     * into a closure (FrameLoad), freezes into an instance (Instance) or is
     * aliased by a pointer (PtrRef). Such frames get a fresh Env; the common case
     * (plain functions, loops) has its Env pooled and reused instead.
     */
    @JvmField val escapes: Boolean =
        ops.any { it.opcode == 0x18 || it.opcode == 0x42 || it.opcode == 0x53 }
}

/**
 * Lexical environment. Variable ids are program-global, so tiny linear maps
 * beat hash maps here.
 *
 * Two layouts share one class:
 *  - **tagged** (the hot path): primitives stay unboxed in `tag`/`prim`/`ref`,
 *    so Load/Store never box. Non-escaping frames pool and [rebind]-reuse these,
 *    so the three arrays cost nothing per call.
 *  - **boxed** (`boxed != null`): a single `Any?` array. Used for frames whose
 *    Env escapes (a closure captures it, it freezes into an instance, or a
 *    pointer aliases it, see FrameSpec.escapes) — those aren't pooled, and one
 *    array is leaner than three when a fresh Env is unavoidable per object.
 */
internal class Env private constructor(
    @JvmField var keys: IntArray,
    @JvmField var parent: Env?,
    @JvmField var tag: ByteArray,
    @JvmField var prim: LongArray,
    @JvmField var ref: Array<Any?>,
    @JvmField var boxed: Array<Any?>?,
) {
    @JvmField var classFrame: Int = -1

    /** Reuse a pooled (always tagged) Env for a fresh activation. */
    fun rebind(keys: IntArray, parent: Env?) {
        this.keys = keys
        this.parent = parent
        this.classFrame = -1
        if (tag.size < keys.size) {
            tag = ByteArray(keys.size); prim = LongArray(keys.size); ref = arrayOfNulls(keys.size)
        } else {
            java.util.Arrays.fill(tag, 0, keys.size, TAG_UNINIT)
            java.util.Arrays.fill(ref, 0, keys.size, null)
        }
    }

    fun localIndex(index: Int): Int {
        val k = keys
        for (i in k.indices) if (k[i] == index) return i
        return -1
    }

    // Boxed access for cold callers (pointers, data-class marshalling, method
    // resolution); the hot Load/Store path reads the slots directly in Fiber.
    fun get(index: Int): Any? {
        var env: Env? = this
        while (env != null) {
            val at = env.localIndex(index)
            if (at >= 0) {
                val b = env.boxed
                if (b != null) {
                    val v = b[at]
                    if (v === Uninitialized) throw VeloError("Variable $index is not initialized")
                    return v
                }
                if (env.tag[at] == TAG_UNINIT) throw VeloError("Variable $index is not initialized")
                return boxSlot(env.tag[at], env.prim[at], env.ref[at])
            }
            env = env.parent
        }
        throw VeloError("Variable $index is not in scope")
    }

    fun set(index: Int, value: Any?) {
        var env: Env? = this
        while (env != null) {
            val at = env.localIndex(index)
            if (at >= 0) {
                val b = env.boxed
                if (b != null) { b[at] = value; return }
                val t = tagOf(value)
                env.tag[at] = t
                env.prim[at] = primBitsOf(value)
                env.ref[at] = if (t == TAG_REF) value else null
                return
            }
            env = env.parent
        }
        throw VeloError("Variable $index is not in scope")
    }

    companion object {
        private val EMPTY_TAG = ByteArray(0)
        private val EMPTY_PRIM = LongArray(0)
        private val EMPTY_REF = arrayOfNulls<Any?>(0)

        fun tagged(keys: IntArray, parent: Env?): Env =
            Env(keys, parent, ByteArray(keys.size), LongArray(keys.size), arrayOfNulls(keys.size), null)

        fun boxed(keys: IntArray, parent: Env?): Env =
            Env(keys, parent, EMPTY_TAG, EMPTY_PRIM, EMPTY_REF, arrayOfNulls<Any?>(keys.size).also { it.fill(Uninitialized) })
    }
}

internal interface TaskOwner {
    val isMain: Boolean
    val suspends: Boolean
    fun submit(task: Runnable)
    fun isCurrentThread(): Boolean
}

internal class FuncValue(val frame: FrameSpec, val captured: Env, val owner: TaskOwner)
internal class CallbackValue(val function: FuncValue)
internal class InstanceValue(val classFrame: Int, val env: Env)
internal class NativeValue(val value: Any, val veloName: String)

/**
 * Adaptive array. Velo arrays are statically typed, so an `array[int]` only ever
 * holds ints — we back it with a primitive [LongArray] (raw bits, same encoding
 * as the operand stack, so element load/store is a plain long copy with no
 * boxing). `kind` starts UNINIT and is fixed by the first store; a reference or
 * a mixed-type store (tuples, `array[Any]`) promotes the backing to a boxed
 * `Array<Any?>`. Uninitialised cells read as null — matching the reference VM,
 * whose arrays are null-filled.
 */
internal class VArray(@JvmField val size: Int) {
    @JvmField var kind: Byte = TAG_UNINIT
    @JvmField var prim: LongArray? = null
    @JvmField var obj: Array<Any?>? = null

    fun get(index: Int): Any? {
        if (index < 0 || index >= size) throw ArrayIndexOutOfBoundsException("Index $index out of bounds for length $size")
        return when (kind) {
            TAG_UNINIT -> null
            TAG_REF -> obj!![index]
            else -> boxSlot(kind, prim!![index], null)
        }
    }

    fun set(index: Int, value: Any?) {
        if (index < 0 || index >= size) throw ArrayIndexOutOfBoundsException("Index $index out of bounds for length $size")
        val t = tagOf(value)
        when {
            kind == TAG_REF -> obj!![index] = value
            kind == t -> prim!![index] = primBitsOf(value)
            kind == TAG_UNINIT -> {
                specialize(t)
                if (t == TAG_REF) obj!![index] = value else prim!![index] = primBitsOf(value)
            }
            else -> { promoteToObj(); obj!![index] = value }
        }
    }

    internal fun specialize(t: Byte) {
        kind = t
        if (t == TAG_REF) obj = arrayOfNulls(size) else prim = LongArray(size)
    }

    internal fun promoteToObj() {
        val boxed = arrayOfNulls<Any?>(size)
        val p = prim
        if (p != null) { val k = kind; for (i in 0 until size) boxed[i] = boxSlot(k, p[i], null) }
        obj = boxed; prim = null; kind = TAG_REF
    }

    fun copyInto(dst: VArray, srcPos: Int, dstPos: Int, length: Int) {
        if (length == 0) return
        if (kind != TAG_UNINIT && kind != TAG_REF && dst.kind == TAG_UNINIT) dst.specialize(kind)
        when {
            kind == dst.kind && kind != TAG_UNINIT && kind != TAG_REF ->
                System.arraycopy(prim!!, srcPos, dst.prim!!, dstPos, length)
            kind == TAG_REF && dst.kind == TAG_REF ->
                System.arraycopy(obj!!, srcPos, dst.obj!!, dstPos, length)
            else -> for (i in 0 until length) dst.set(dstPos + i, get(srcPos + i))
        }
    }
}

internal interface VPointer {
    fun get(): Any?
    fun set(value: Any?)
}

internal class BoxPointer(private var value: Any?) : VPointer {
    override fun get(): Any? = value
    override fun set(value: Any?) { this.value = value }
}

internal class EnvPointer(private val env: Env, private val index: Int) : VPointer {
    override fun get(): Any? = env.get(index)
    override fun set(value: Any?) = env.set(index, value)
}

internal class ArrayPointer(private val array: VArray, private val index: Int) : VPointer {
    override fun get(): Any? = array.get(index)
    override fun set(value: Any?) = array.set(index, value)
}
