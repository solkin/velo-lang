package nodes

import Environment

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Any>) = value
}