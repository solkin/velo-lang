package nodes

import Environment

data class DoubleNode(
    val value: Double,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = DoubleType(value)
}

class DoubleType(val value: Double) : Type<Double>(value)
