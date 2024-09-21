package compiler

import compiler.nodes.Type
import vm2.Operation

data class CompilerContext(
    private val ops: MutableList<Operation>,
    private val vars: MutableMap<String, Var>,
) {

    fun getVar(name: String): Var? {
        return vars[name]
    }

    fun defVar(name: String, type: Type): Var {
        // TODO: check scopes
        val exist = vars[name]
        if (exist != null) {
            return exist
        }
        val v = Var(index = vars.size, type = type)
        vars[name] = v
        return v
    }

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

    fun fork(): CompilerContext {
        return CompilerContext(ops = ArrayList(), vars = vars)
    }

    fun merge(ctx: CompilerContext) {
        this.ops.addAll(ctx.ops)
    }
}

data class Var(
    val index: Int,
    val type: Type,
)
