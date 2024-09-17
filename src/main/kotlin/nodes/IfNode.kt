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

    override fun compile(ctx: CompilerContext): DataType {
        val thenCtx = ctx.fork()
        val thenType = thenNode.compile(thenCtx)

        val elseCtx = ctx.fork()
        val elseType = elseNode?.let { elseNode ->
            val type = elseNode.compile(elseCtx)
            thenCtx.add(Move(elseCtx.size()))
            type
        } ?: DataType.VOID

        if (elseType != DataType.VOID && thenType != elseType) {
            throw IllegalArgumentException("Then and else return types are differ: $thenType / $elseType")
        }

        condNode.compile(ctx)
        val elseSkip = thenCtx.size()
        ctx.add(If(elseSkip))
        ctx.merge(thenCtx)
        if (elseCtx.isNotEmpty()) {
            ctx.merge(elseCtx)
        }
        return thenType
    }
}