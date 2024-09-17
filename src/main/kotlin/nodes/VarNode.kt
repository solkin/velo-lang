package nodes

import CompilerContext
import Environment
import vm2.operations.Get

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = env.get(name)

    override fun compile(ctx: CompilerContext): DataType {
        val v = ctx.getVar(name) ?: throw IllegalArgumentException("Variable $name is not defined")
        ctx.add(Get(v.index))
        return v.type
    }
}
