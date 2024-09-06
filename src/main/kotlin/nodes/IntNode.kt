package nodes

import Environment
import vm2.Operation
import vm2.operations.Push

data class IntNode(
    val value: Int,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = IntType(value)

    override fun compile(ops: MutableList<Operation>) {
        ops.add(Push(value))
    }
}

class IntType(val value: Int) : Type<Int>(value)
