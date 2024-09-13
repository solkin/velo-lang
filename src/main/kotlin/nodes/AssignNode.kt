package nodes

import CompilerContext
import Environment
import vm2.operations.Set

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        return env.set(left.name, right.evaluate(env))
    }

    override fun compile(ctx: CompilerContext) {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        right.compile(ctx)
        ctx.add(Set(ctx.varIndex(left.name)))
    }
}