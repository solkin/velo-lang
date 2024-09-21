package compiler.nodes

import compiler.CompilerContext
import compiler.Environment
import vm.operations.Get

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = env.get(name)

    override fun compile(ctx: CompilerContext): Type {
        val v = ctx.getVar(name) ?: throw IllegalArgumentException("Variable $name is not defined")
        ctx.add(Get(v.index))
        return v.type
    }
}
