package compiler.nodes

import core.Op

import compiler.Context

data class VarNode(
    val name: String,
    val typeArgs: List<Type> = emptyList(),
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        val v = ctx.get(name)
        ctx.add(Op.Load(v.index))
        if (typeArgs.isNotEmpty() && v.type is ClassType) {
            return (v.type as ClassType).copy(typeArgs = typeArgs)
        }
        return v.type
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val v = ctx.get(name)
        if (v.immutable) {
            throw IllegalArgumentException("Cannot reassign immutable '$name' (a `let` binding and data class fields are immutable)")
        }
        if (!v.type.sameAs(type)) {
            throw IllegalArgumentException("Illegal var assign type $type != ${v.type}")
        }
        // Clarify the variable type from the right-hand side — but not for an
        // interface-typed variable (it must keep its interface type so dispatch
        // stays dynamic), and never collapse a pointer to NullType.
        if (type !is NullType && v.type !is InterfaceType) {
            ctx.retype(name, type)
        }
        ctx.add(Op.Store(v.index))
    }
}
