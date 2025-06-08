package compiler

import compiler.nodes.Type
import vm.Operation
import java.util.concurrent.atomic.AtomicInteger

data class Context(
    val parent: Context?,
    val frame: CompilerFrame,
    val frameCounter: AtomicInteger,
    val subFrame: Boolean = false,
) {

    private val frames: MutableList<CompilerFrame> = ArrayList()

    fun add(op: Operation) {
        frame.ops.add(op)
    }

    fun addAll(ops: List<Operation>) {
        frame.ops.addAll(ops)
    }

    fun size(): Int {
        return frame.ops.size
    }

    fun isNotEmpty(): Boolean {
        return frame.ops.isNotEmpty()
    }

    fun operations(): List<Operation> {
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

    fun get(name: String): Var {
        val context = lookup(name)
        return context?.frame?.vars?.get(name) ?:
            throw IllegalArgumentException("Undefined variable $name")
    }

    fun retype(name: String, type: Type) {
        lookup(name)?.frame?.retype(name, type) ?: throw IllegalArgumentException("Undefined variable $name")
    }

    fun def(name: String, type: Type): Var {
        return frame.def(name, type)
    }

}
