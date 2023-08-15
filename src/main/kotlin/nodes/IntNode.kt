package nodes

import Environment

data class IntNode(
    val value: Int,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = IntType(value)
}

class IntType(val value: Int) : Type<Int>(value)
