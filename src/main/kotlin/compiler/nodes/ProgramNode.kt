package compiler.nodes

import compiler.Context

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        var type: Type = VoidType
        prog.forEach { node ->
            type = node.compile(ctx)
        }
        return type
    }
}
