package compiler.nodes

import compiler.Context
import vm.operations.Ext
import vm.operations.Free

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Ext())
        ctx.enumerator.extend()
        vars.forEach { it.compile(ctx) }
        val type = body.compile(ctx)
        ctx.add(Free())
        ctx.enumerator.free()
        return type
    }
}
