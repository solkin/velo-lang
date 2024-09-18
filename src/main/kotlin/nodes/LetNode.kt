package nodes

import CompilerContext
import Environment
import vm2.operations.Ext
import vm2.operations.Free

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val scope = env.extend()
        vars.forEach { v ->
            v.evaluate(scope)
        }
        return body.evaluate(scope)
    }

    override fun compile(ctx: CompilerContext): Int {
        ctx.add(Ext())
        vars.forEach { it.compile(ctx) }
        val type = body.compile(ctx)
        ctx.add(Free())
        return type
    }
}