package nodes

import Environment
import vm2.Operation
import vm2.operations.Set

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        return env.set(left.name, right.evaluate(env))
    }

    override fun compile(ops: MutableList<Operation>) {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        right.compile(ops)
        ops.add(Set(left.name.hashCode()))
    }
}