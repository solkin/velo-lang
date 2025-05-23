package compiler

import compiler.nodes.Type
import vm.Operation
import java.util.concurrent.atomic.AtomicInteger

data class Context(
    val parent: Context?,
    val frame: Frame,
) {

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

    fun extend(): Context {
        return Context(
            parent = this,
            Frame(num = frame.num + 1, ops = mutableListOf(), vars = mutableMapOf(), counter = AtomicInteger(frame.counter.get())),
        )
    }

    fun inner(): Context {
        return Context(
            parent = this,
            Frame(num = frame.num, ops = mutableListOf(), vars = mutableMapOf(), counter = frame.counter),
        )
    }

    fun merge(ctx: Context) {
        frame.ops.addAll(ctx.operations())
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
        return context?.frame?.vars?.get(name) ?: throw IllegalArgumentException("Undefined variable $name")
    }

    fun def(name: String, type: Type): Var {
        return frame.def(name, type)
    }

}
