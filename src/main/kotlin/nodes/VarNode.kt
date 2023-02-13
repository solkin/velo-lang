package nodes

import Environment

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Any>) = env.get(name)
}