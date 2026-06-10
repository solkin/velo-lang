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
        val v = ctx.def(name, defType)
        ctx.add(Op.Store(v.index))
        return VoidType
    }
}
