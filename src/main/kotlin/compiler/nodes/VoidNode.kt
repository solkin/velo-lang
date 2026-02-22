package compiler.nodes

import compiler.Context

object VoidNode : Node() {
    override fun compile(ctx: Context): Type {
        return VoidType
    }
}

object VoidType : Type {
    override fun sameAs(type: Type): Boolean {
        return type is VoidType
    }

    override fun default(ctx: Context) {}

    override fun prop(name: String): Prop? = null

    override fun log() = name()

    override fun vmType() = vm.VmType.Void

    override fun name() = "void"
}
