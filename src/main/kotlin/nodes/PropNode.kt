package nodes

import Environment

data class PropNode(
    val name: String,
    val parent: Node
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val v = parent.evaluate(env)
        return v.property(name)
    }
}