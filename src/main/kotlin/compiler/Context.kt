package compiler

import compiler.nodes.Type
import vm.Operation

data class Context(
    private val ops: MutableList<Operation>,
    val heap: Heap,
) {

    fun add(op: Operation) {
        ops.add(op)
    }

    fun addAll(ops: List<Operation>) {
        this.ops.addAll(ops)
    }

    fun size(): Int {
        return ops.size
    }

    fun isNotEmpty(): Boolean {
        return ops.isNotEmpty()
    }

    fun operations(): List<Operation> {
        return ops
    }

    fun fork(): Context {
        return Context(ops = ArrayList(), heap)
    }

    fun merge(ctx: Context) {
        this.ops.addAll(ctx.ops)
    }
}

data class Var(
    val index: Int,
    val type: Type,
)
