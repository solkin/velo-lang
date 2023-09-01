package nodes

import Environment

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val scope = env.extend()
        vars.forEach { v ->
            v.evaluate(scope)
        }
        return body.evaluate(scope)
    }
}