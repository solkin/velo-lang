package nodes

import Environment

data class WhileNode(
    val cond: Node,
    val expr: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        while (cond.evaluate(env).value() != false) {
            expr.evaluate(env)
        }
        return BoolType(false)
    }
}
