package compiler.nodes

import compiler.Context

class VoidNode : Node() {
    override fun compile(ctx: Context): Type {
        return VoidType
    }
}

object VoidType : Type {
    override val type: BaseType
        get() = BaseType.VOID

    override fun default(ctx: Context) {}

    override fun prop(name: String): Prop? = null
}
