package nodes

import Environment

data class NumNode(
    val value: Double,
) : Node() {
    override fun evaluate(env: Environment<Any>) = value
}