package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Get

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = env.get(name)

    override fun compile(ctx: Context): Type {
        val v = ctx.heap.current().get(name)
        ctx.add(Get(v.index))
        return v.type
    }
}
