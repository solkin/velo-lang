package nodes

import CompilerContext
import Environment
import vm2.operations.If
import vm2.operations.Move

data class WhileNode(
    val cond: Node,
    val expr: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        while (cond.evaluate(env).value() != false) {
            expr.evaluate(env)
        }
        return BoolType(false)
    }

    override fun compile(ctx: CompilerContext) {
        val condCtx = ctx.fork()
        cond.compile(condCtx)

        val exprCtx = ctx.fork()
        expr.compile(exprCtx)
        exprCtx.add(Move(-(exprCtx.size() + condCtx.size() + 2))) // +2 because to move and if is not included

        ctx.merge(condCtx)
        ctx.add(If(exprCtx.size()))
        ctx.merge(exprCtx)
    }
}
