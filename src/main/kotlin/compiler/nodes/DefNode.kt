package compiler.nodes

import compiler.Context
import vm.operations.Set

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
        if (type != defType && type.type != AutoType.type) {
            throw IllegalArgumentException("Illegal assign type $defType != $type")
        }
        val v = ctx.def(name, defType)
        ctx.add(Set(v.index))
        return VoidType
    }
}
