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
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        val cond = condNode.evaluate(env)
        if (cond.value() != false) return thenNode.evaluate(env)
        return elseNode?.let { elseNode.evaluate(env) } ?: BoolValue(false)
    }

    override fun compile(ctx: CompilerContext): Type {
        val thenCtx = ctx.fork()
        val thenType = thenNode.compile(thenCtx)

        val elseCtx = ctx.fork()
        val elseType = elseNode?.let { elseNode ->
            val type = elseNode.compile(elseCtx)
            thenCtx.add(Move(elseCtx.size()))
            type
        } ?: VoidType

        if (elseType.type == VoidType.type && thenType.type != elseType.type) {
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