package compiler.nodes

import compiler.Context
import vm.operations.Frame

data class ClassNode(
    val name: String,
    val defs: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        return ClassType(name)
    }
}

data class ClassType(val name: String) : Type {
    override val type: BaseType
        get() = BaseType.CLASS

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop? = null
}
