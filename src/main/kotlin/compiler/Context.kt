package compiler

import vm.Operation

data class Context(
    private val ops: MutableList<Operation>,
    val scope: Scope,
    val types: TypeRegistry,
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

    fun extend(): Context {
        return Context(ops = ArrayList(), scope.extend(), types)
    }

    fun inner(): Context {
        return Context(ops = ArrayList(), scope, types)
    }

    fun merge(ctx: Context) {
        this.ops.addAll(ctx.ops)
    }
}
