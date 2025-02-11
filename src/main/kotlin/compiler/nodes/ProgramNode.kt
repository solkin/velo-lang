package compiler.nodes

import compiler.Context
import vm.operations.Drop

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        ctx.enumerator.extend()
        var type: Type = VoidType
        prog.forEachIndexed { index, node ->
            type = node.compile(ctx)
            if (type != VoidType && index != prog.size-1) {
                ctx.add(Drop())
            }
        }
        ctx.enumerator.free()
        return type
    }
}
