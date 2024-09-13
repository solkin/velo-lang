import vm2.Operation

data class CompilerContext(
    private val ops: MutableList<Operation>,
    private val vars: MutableMap<String, Int>,
) {

    fun varIndex(name: String): Int {
        return vars[name] ?: let {
            val index = vars.size
            vars[name] = vars.size
            index
        }
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