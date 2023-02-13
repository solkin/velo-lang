package nodes

import Environment

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun evaluate(env: Environment<Any>) = value
}

val FALSE = BoolNode(value = false)