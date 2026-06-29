package compiler.nodes

import core.Op

import compiler.Context

data class DefNode(
    val name: String,
    val type: Type,
    val def: Node?,
) : Node() {
    override fun compile(ctx: Context): Type {
        val defType = def?.compile(ctx) ?: let {
            type.default(ctx)
            type
        }
        if (!type.sameAs(defType) && !type.sameAs(AnyType)) {
            throw IllegalArgumentException("Illegal assign type $defType != $type")
        }
        // An interface-typed declaration keeps the interface as the variable's
        // static type (so dispatch stays dynamic and only interface methods are
        // callable); other declarations keep the more precise initializer type.
        val v = ctx.def(name, if (type is InterfaceType) type else defType)
        ctx.add(Op.Store(v.index))
        return VoidType
    }
}
