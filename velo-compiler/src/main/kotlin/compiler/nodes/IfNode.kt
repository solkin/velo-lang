package compiler.nodes

import core.Op

import compiler.Context

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val thenCtx = ctx.extend()
        val thenType = thenNode.compile(thenCtx)
        thenCtx.add(Op.Ret)

        val elseCtx = ctx.extend()
        val elseType = elseNode?.compile(elseCtx)
        elseCtx.add(Op.Ret)

        val returnType = if (elseType == null || thenType.sameAs(elseType)) thenType else AnyType

        condNode.compile(ctx)

        ctx.add(Op.If(elseSkip = 2))
        ctx.add(Op.Frame(thenCtx.frame.num))
        ctx.add(Op.Move(count = 1))
        ctx.add(Op.Frame(elseCtx.frame.num))
        ctx.add(Op.Call(args = 0))
        ctx.merge(thenCtx)
        ctx.merge(elseCtx)
        return returnType
    }
}
