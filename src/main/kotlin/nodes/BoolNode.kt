package nodes

import Environment

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = BoolType(value)
}

class BoolType(val value: Boolean) : Type<Boolean>(value)

val FALSE = BoolNode(value = false)