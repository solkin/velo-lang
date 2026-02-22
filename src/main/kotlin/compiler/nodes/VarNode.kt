package compiler.nodes

import compiler.Context
import vm.operations.Load
import vm.operations.Store

data class VarNode(
    val name: String,
    val typeArgs: List<Type> = emptyList(),
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        val v = ctx.get(name)
        ctx.add(Load(v.index))
        if (typeArgs.isNotEmpty() && v.type is ClassType) {
            return (v.type as ClassType).copy(typeArgs = typeArgs)
        }
        return v.type
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val v = ctx.get(name)
        if (!v.type.sameAs(type)) {
            throw IllegalArgumentException("Illegal var assign type $type != ${v.type}")
        }
        // Don't retype to NullType - keep the original pointer type
        if (type !is NullType) {
            ctx.retype(name, type) // Clarify variable type from the right side of assignment
        }
        ctx.add(Store(v.index))
    }
}
