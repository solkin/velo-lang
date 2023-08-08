package nodes

import Environment

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val v = parent.evaluate(env)
        val a = args?.map { it.evaluate(env) }
        return v.property(name, a)
    }
}