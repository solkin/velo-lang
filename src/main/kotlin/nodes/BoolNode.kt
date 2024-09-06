package nodes

import Environment
import vm2.Operation
import vm2.operations.Push

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = BoolType(value)

    override fun compile(ops: MutableList<Operation>) {
        ops.add(Push(value))
    }
}

class BoolType(val value: Boolean) : Type<Boolean>(value)

val FALSE = BoolNode(value = false)