package nodes

import CompilerContext
import Environment
import vm2.operations.If
import vm2.operations.Move

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val cond = condNode.evaluate(env)
        if (cond.value() != false) return thenNode.evaluate(env)
        return elseNode?.let { elseNode.evaluate(env) } ?: BoolType(false)
    }

    override fun compile(ctx: CompilerContext) {
        val thenCtx = ctx.fork()
        thenNode.compile(thenCtx)

        val elseCtx = ctx.fork()
        elseNode?.run {
            compile(elseCtx)
            thenCtx.add(Move(elseCtx.size()))
        }

        condNode.compile(ctx)
        val elseSkip = thenCtx.size()
        ctx.add(If(elseSkip))
        ctx.merge(thenCtx)
        if (elseCtx.isNotEmpty()) {
            ctx.merge(elseCtx)
        }
    }
}