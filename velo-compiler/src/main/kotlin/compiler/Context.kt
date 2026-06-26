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
        return Context(
            parent = this,
            frame = CompilerFrame(
                num = frameCounter.incrementAndGet(),
                ops = mutableListOf(),
                vars = mutableMapOf(),
                varCounter = AtomicInteger(frame.varCounter.get()),
            ),
            frameCounter,
            shared = shared,
        )
    }

    fun discrete(parent: Context? = null): Context {
        return Context(
            parent = parent,
            frame = CompilerFrame(
                num = frameCounter.incrementAndGet(),
                ops = mutableListOf(),
                vars = mutableMapOf(),
                varCounter = AtomicInteger(frame.varCounter.get()),
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
