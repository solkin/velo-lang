package compiler.nodes

import compiler.Context
import vm.operations.Load
import vm.operations.Store

data class VarNode(
    val name: String,
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        val v = ctx.get(name)
        ctx.add(Load(v.index))
        return v.type
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val v = ctx.get(name)
        if (!v.type.sameAs(type)) {
            throw IllegalArgumentException("Illegal var assign type $type != ${v.type}")
        }
        ctx.retype(name, type) // Clarify variable type from the right side of assignment
        ctx.add(Store(v.index))
    }
}
