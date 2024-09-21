package compiler.nodes

import compiler.CompilerContext
import compiler.Environment
import vm.operations.Ext
import vm.operations.Free

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        val scope = env.extend()
        vars.forEach { v ->
            v.evaluate(scope)
        }
        return body.evaluate(scope)
    }

    override fun compile(ctx: CompilerContext): Type {
        ctx.add(Ext())
        vars.forEach { it.compile(ctx) }
        val type = body.compile(ctx)
        ctx.add(Free())
        return type
    }
}