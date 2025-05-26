package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.IfElse
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

        if (elseType != null && thenType.type != elseType.type) {
            throw IllegalArgumentException("Then and else return types are differ: $thenType / $elseType")
        }

        condNode.compile(ctx)

        ctx.add(IfElse(thenNum = thenCtx.frame.num, elseNum = elseCtx.frame.num))
        ctx.add(Call(args = 0))
        ctx.merge(thenCtx)
        ctx.merge(elseCtx)
        return thenType
    }
}
