package nodes

import Environment

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = env.get(name)
}
