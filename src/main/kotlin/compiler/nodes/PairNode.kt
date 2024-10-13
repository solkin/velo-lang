package compiler.nodes

import compiler.Context
import vm.operations.Push

data class PairNode(
    val first: Node,
    val second: Node?,
) : Node()

data class PairType(val first: Type, val second: Type) : Type {
    override val type: BaseType
        get() = BaseType.PAIR

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
        ctx.add(Push(value = 0))
    }
}
