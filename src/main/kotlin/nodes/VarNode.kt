package nodes

import CompilerContext
import Environment
import vm2.operations.*

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = env.get(name)

    override fun compile(ctx: CompilerContext) {
        ctx.add(Get(ctx.varIndex(name)))
    }
}
