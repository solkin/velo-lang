package nodes

import CompilerContext
import Environment
import vm2.operations.Def
import vm2.operations.Push

data class DefNode(
    val name: String,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val value = def?.let { def.evaluate(env) } ?: VoidType()
        env.def(name, value)
        return value
    }

    override fun compile(ctx: CompilerContext) {
        def?.compile(ctx) ?: let { ctx.add(Push(value = 0)) }
        ctx.add(Def(ctx.varIndex(name)))
    }
}
