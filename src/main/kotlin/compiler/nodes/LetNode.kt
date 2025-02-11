package compiler.nodes

import compiler.Context

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
////        ctx.add(Ext())
//        ctx.enumerator.extend()
//        vars.forEach { it.compile(ctx) }
//        val type = body.compile(ctx)
////        ctx.add(Free())
//        ctx.enumerator.free()

        val type = ctx.wrapScope { context ->
//        context.add(Ext())
            context.enumerator.extend()
            vars.forEach { it.compile(context) }
            val type = body.compile(context)
//        context.add(Free())
            context.enumerator.free()
            type
        }

        return type
    }
}
