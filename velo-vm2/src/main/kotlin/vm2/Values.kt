package vm2

import core.Op
import core.SerializedFrame

/**
 * A bytecode frame prepared for execution, computed once at load time (not per
 * activation). Built from [SerializedFrame] when the program is linked.
 *
 * The compiler forks each frame's var counter from its parent, so the indices a
 * frame declares form a contiguous block `[base, base + slotCount)`. That lets
 * a [Frame] hold its locals in a flat array indexed by `index - base` instead of
 * a hash map. [tags] mirrors [ops] with each op's integer opcode precomputed, so
 * the interpreter dispatches through a single `when` over `Int` (a JVM
 * tableswitch) rather than a chain of `is Op.X` checks.
 */
class FrameSpec(val num: Int, val ops: List<Op>, val base: Int, val slotCount: Int) {

    @JvmField val tags: IntArray = IntArray(ops.size) { ops[it].opcode }

    constructor(frame: SerializedFrame) : this(
        frame.num,
        frame.ops,
        if (frame.vars.isEmpty()) 0 else frame.vars.min(),
        if (frame.vars.isEmpty()) 0 else frame.vars.max() - frame.vars.min() + 1,
    )

    companion object {
        /** The empty spec backing a placeholder scope (e.g. the system actor). */
        val EMPTY = FrameSpec(-1, emptyList(), 0, 0)
    }
}

/**
 * One fiber's operand stack: a single growable array shared by every active
 * frame on that fiber. A call does **not** allocate an operand buffer — it runs
 * in a window `[`[Frame.opBase]`, `[top]`)` at the top of this stack, and the
 * caller's arguments are already sitting at the top, so they become the callee's
 * operands in place (no copy). Returning rewinds [top] to the caller's window
 * (see [Frame.retBase]). Values live contiguously (cache-friendly), and a
 * suspended fiber simply keeps its array intact.
 *
 * Only the current (top-of-call-stack) frame ever pushes or pops, so [push] /
 * [pop] / [peek] act on the global [top]; outer frames' operands sit untouched
 * below.
 */
class ValueStack {
    @JvmField var a: Array<Any?> = arrayOfNulls(256)
    @JvmField var top: Int = 0

    fun push(v: Any?) {
        if (top == a.size) a = a.copyOf(a.size shl 1)
        a[top++] = v
    }

    fun pop(): Any? = a[--top]
    fun peek(): Any? = a[top - 1]
}

/**
 * One call's activation **and** the variable scope it owns, merged into a single
 * object: a call allocates one [Frame] (plus a locals array only if it declares
 * locals) — its operands live in the fiber's shared [ValueStack], not a per-call
 * buffer. A closure or instance captures the [Frame] itself as its lexical
 * parent, so the scope it represents survives after the call returns.
 *
 *  - **Execution:** [ip] is the instruction cursor. This frame's operands occupy
 *    the value-stack window starting at [opBase]; on return the stack is rewound
 *    to [retBase], which also drops the call's arguments and any receiver.
 *  - **Scope:** locals live in [slots], a flat array for the contiguous block
 *    `[spec.base, spec.base + spec.slotCount)`, [EMPTY] until first written so a
 *    stored `null` is distinct from "not yet assigned". [store]/[load] resolve
 *    along the lexical [parent] chain (so an inner scope can mutate an outer
 *    variable — closures over mutable state work). Locals are heap-resident, so
 *    capturing a frame (closure / instance / pointer / actor) is always safe,
 *    independent of where the value stack's [top] currently is.
 */
class Frame(@JvmField val spec: FrameSpec, @JvmField val parent: Frame?) {

    @JvmField var ip: Int = 0

    /**
     * The innermost **environment** scope currently active in this call — the
     * frame itself, or a per-iteration loop scope pushed by `Op.ScopeEnter`.
     * Locals, closure capture and address-of resolve through `scope`; execution
     * (ip, operands) stays on the frame. This is what separates environment from
     * control, so a loop body can rebind its locals per iteration without a call.
     */
    @JvmField var scope: Frame = this

    /** Start of this frame's operand window in its fiber's [ValueStack]. */
    @JvmField var opBase: Int = 0

    /** Where the value stack's `top` rewinds to when this frame returns (drops args + receiver). */
    @JvmField var retBase: Int = 0

    /**
     * Active `try` error handlers, innermost last (VEL-9). Null until the first
     * [core.Op.TryEnter] in this frame. Kept on the frame, so it rides along with
     * the fiber's heap-resident call stack across an `await` suspension.
     */
    @JvmField var handlers: ArrayDeque<Handler>? = null

    // ---- scope (locals along the lexical chain) ----
    private val slots: Array<Any?>? = if (spec.slotCount > 0) Array(spec.slotCount) { EMPTY } else null
    private var overflow: HashMap<Int, Any?>? = null

    fun store(index: Int, value: Any?) {
        var f: Frame? = this
        while (f != null) {
            val sl = f.slots
            if (sl != null) {
                val k = index - f.spec.base
                if (k >= 0 && k < sl.size) { sl[k] = value; return }
            }
            f = f.parent
        }
        (overflow ?: HashMap<Int, Any?>().also { overflow = it })[index] = value
    }

    fun load(index: Int): Any? {
        var f: Frame? = this
        while (f != null) {
            val sl = f.slots
            if (sl != null) {
                val k = index - f.spec.base
                if (k >= 0 && k < sl.size) {
                    val v = sl[k]
                    if (v !== EMPTY) return v
                }
            }
            val ov = f.overflow
            if (ov != null && ov.containsKey(index)) return ov[index]
            f = f.parent
        }
        throw IllegalStateException("Variable #$index is not initialised in this scope chain")
    }

    /** This frame's locals, for GC traversal (operands are rooted via the value stack). */
    fun localValues(): Collection<Any?> {
        val written = slots?.filter { it !== EMPTY } ?: emptyList()
        val ov = overflow ?: return written
        return written + ov.values
    }

    private companion object {
        /** Sentinel for a declared-but-unwritten slot. Never escapes the frame. */
        val EMPTY = Any()
    }
}

/**
 * Runtime value model for velo-vm2.
 *
 * Operand-stack values are raw Kotlin objects wherever a primitive maps
 * cleanly: `Int`, `Float`, `Byte`, `Boolean`, `String`. Everything with
 * reference identity gets a wrapper below. A native object is stored as its
 * plain JVM instance (`Any`), opaque to the VM.
 */

/** A Velo array (also backs tuples — a tuple is a fixed-size array). */
class VArray(val data: Array<Any?>) {
    val size: Int get() = data.size
}

/**
 * A function value: the target bytecode [frame] plus the lexical scope
 * captured at the `Op.Frame` that produced it (definition-site scoping). The
 * capture is the defining [Frame] itself.
 */
class FuncValue(val frame: FrameSpec, val captured: Frame)

/**
 * A class instance — the frozen activation [scope] (a [Frame]) of a `new X(...)`
 * call. [frameNum] identifies the class frame, used to recognise `data class`
 * instances for by-value equality.
 */
class Instance(val scope: Frame, val frameNum: Int)

/** The `nullptr` literal value. */
object NullPtrValue {
    override fun toString() = "nullptr"
}

/** A pointer: a mutable cell the program can read through and write through. */
sealed interface Ptr {
    fun get(): Any?
    fun set(value: Any?)
}

/** `ptr.new(v)` — a standalone boxed cell. */
class BoxPtr(private var value: Any?) : Ptr {
    override fun get() = value
    override fun set(value: Any?) {
        this.value = value
    }
}

/** `&localVar` — aliases a variable slot in a [Frame]. */
class VarPtr(private val scope: Frame, private val index: Int) : Ptr {
    override fun get() = scope.load(index)
    override fun set(value: Any?) = scope.store(index, value)
}

/** `&array[i]` — aliases one element of a [VArray]. */
class ArrayPtr(private val array: VArray, private val index: Int) : Ptr {
    override fun get() = array.data[index]
    override fun set(value: Any?) {
        array.data[index] = value
    }
}
