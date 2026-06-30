package compiler

import compiler.nodes.Type
import core.NativeClassDescriptor
import core.NativeRef
import core.NativeRegistry
import core.Op
import java.util.concurrent.atomic.AtomicInteger

/**
 * Compilation-wide state shared by every [Context] in a program, however
 * the context tree branches (`extend`/`discrete`/`inner`).
 *
 * Holds the [NativeRegistry] the program is compiled against and the
 * **native pool** — the ordered, deduplicated list of every native entry
 * point the code references. `NativeCall` ops carry indexes into this pool;
 * the pool itself is serialized into the program (and `.vbc`) and linked
 * against the host registry at load time.
 */
class CompilerShared(
    val nativeRegistry: NativeRegistry? = null,
) {
    val nativePool = mutableListOf<NativeRef>()

    /** Marshalling metadata for every `data class` the program declares. */
    val dataClasses = mutableListOf<core.DataClassInfo>()

    /** Per-class method tables (name -> instance slot) for interface dispatch. */
    val classMethods = mutableListOf<core.ClassMethodsInfo>()

    fun intern(ref: NativeRef): Int {
        val existing = nativePool.indexOf(ref)
        if (existing >= 0) return existing
        nativePool.add(ref)
        return nativePool.size - 1
    }

    fun descriptor(veloName: String): NativeClassDescriptor? =
        nativeRegistry?.descriptor(veloName)
}

data class Context(
    val parent: Context?,
    val frame: CompilerFrame,
    val frameCounter: AtomicInteger,
    val subFrame: Boolean = false,
    val shared: CompilerShared = CompilerShared(),
) {

    private val frames: MutableList<CompilerFrame> = ArrayList()

    /**
     * The declared return type of the function this context belongs to, set by
     * [compiler.nodes.FuncNode] on the function's root context. `null` on inline
     * block/loop contexts (which chain to the function via [parent]) and at the
     * program top level. A `return` reads it through [enclosingReturnType].
     */
    var returnType: Type? = null

    /** The declared return type of the nearest enclosing function, or `null`. */
    fun enclosingReturnType(): Type? {
        var context: Context? = this
        while (context != null) {
            context.returnType?.let { return it }
            context = context.parent
        }
        return null
    }

    /**
     * Marks the body context of a loop, so a `break`/`continue` can confirm it
     * is inside one. Set by the loop nodes on their body's [block] context.
     */
    var loopBody: Boolean = false

    /**
     * Whether this context lies inside a loop body *within the current
     * function*. The walk stops at the function root (a non-[subFrame] frame),
     * so `break`/`continue` cannot escape into an enclosing function's loop.
     */
    fun isInLoop(): Boolean {
        var context: Context? = this
        while (context != null) {
            if (context.loopBody) return true
            if (!context.subFrame) return false
            context = context.parent
        }
        return false
    }

    fun add(op: Op) {
        frame.ops.add(op)
    }

    /** Overwrite a previously emitted op (used to backpatch jump distances). */
    fun replace(index: Int, op: Op) {
        frame.ops[index] = op
    }

    fun addAll(ops: List<Op>) {
        frame.ops.addAll(ops)
    }

    fun size(): Int {
        return frame.ops.size
    }

    fun isNotEmpty(): Boolean {
        return frame.ops.isNotEmpty()
    }

    fun operations(): List<Op> {
        return frame.ops
    }

    fun frames(): List<CompilerFrame> {
        return if (subFrame) frames else frames.plus(frame)
    }

    fun extend(): Context {
        val base = frame.varCounter.get()
        return Context(
            parent = this,
            frame = CompilerFrame(
                num = frameCounter.incrementAndGet(),
                ops = mutableListOf(),
                vars = mutableMapOf(),
                varCounter = AtomicInteger(base),
                varBase = base,
            ),
            frameCounter,
            shared = shared,
        )
    }

    fun discrete(parent: Context? = null): Context {
        val base = frame.varCounter.get()
        return Context(
            parent = parent,
            frame = CompilerFrame(
                num = frameCounter.incrementAndGet(),
                ops = mutableListOf(),
                vars = mutableMapOf(),
                varCounter = AtomicInteger(base),
                varBase = base,
            ),
            frameCounter,
            shared = shared,
        )
    }

    fun inner(): Context {
        return Context(
            parent = this,
            frame = CompilerFrame(
                num = frame.num,
                ops = mutableListOf(),
                vars = frame.vars,
                varCounter = frame.varCounter,
                varBase = frame.varBase,
            ),
            frameCounter,
            subFrame = true,
            shared = shared,
        )
    }

    /**
     * A lexical block within the current VM frame: ops merge inline (so jumps
     * like `if`/`while`/`break`/`return` stay in one frame), variable slots are
     * drawn from the same [CompilerFrame.varCounter] (one VM variable array),
     * but names live in a fresh map chained to the parent — so a block-local
     * declaration shadows without leaking out of the block, the scoping a
     * closure used to provide before control flow was inlined.
     */
    fun block(): Context {
        return Context(
            parent = this,
            frame = CompilerFrame(
                num = frame.num,
                ops = mutableListOf(),
                vars = mutableMapOf(),
                varCounter = frame.varCounter,
                varBase = frame.varBase,
            ),
            frameCounter,
            subFrame = true,
            shared = shared,
        )
    }

    fun merge(ctx: Context) {
        frames.addAll(ctx.frames())
        if (ctx.subFrame) {
            frame.ops.addAll(ctx.operations())
        }
    }

    private fun lookup(name: String): Context? {
        var context: Context? = this
        while (context != null) {
            if (context.frame.vars.contains(name)) {
                return context
            }
            context = context.parent
        }
        return null
    }

    fun opt(name: String): Var? = lookup(name)?.frame?.vars?.get(name)

    fun get(name: String): Var = opt(name) ?: throw IllegalArgumentException("Undefined variable $name on get")

    fun retype(name: String, type: Type) {
        lookup(name)?.frame?.retype(name, type) ?: throw IllegalArgumentException("Undefined variable $name on retype")
    }

    fun def(name: String, type: Type, immutable: Boolean = false): Var {
        return frame.def(name, type, immutable)
    }

}
