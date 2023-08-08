package nodes

import Environment

data class VardefNode(
    val name: String,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = env.get(name)
}
