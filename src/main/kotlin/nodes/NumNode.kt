package nodes

import Environment

data class NumNode(
    val value: Double,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = NumType(value)
}

class NumType(val value: Double) : Type<Double>(value)
