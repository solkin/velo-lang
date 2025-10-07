package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.Frame
import vm.operations.If
import vm.operations.Move
import vm.operations.Ret

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val thenCtx = ctx.extend()
        val thenType = thenNode.compile(thenCtx)
        thenCtx.add(Ret())

        val elseCtx = ctx.extend()
        val elseType = elseNode?.compile(elseCtx)
        elseCtx.add(Ret())

        val returnType = if (elseType == null || thenType.sameAs(elseType)) thenType else AnyType

        condNode.compile(ctx)

        ctx.add(If(elseSkip = 2))
        ctx.add(Frame(thenCtx.frame.num))
        ctx.add(Move(count = 1))
        ctx.add(Frame(elseCtx.frame.num))
        ctx.add(Call(args = 0))
        ctx.merge(thenCtx)
        ctx.merge(elseCtx)
        return returnType
    }
}
