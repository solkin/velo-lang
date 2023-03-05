package nodes

import Environment

data class WhileNode(
    val cond: Node,
    val expr: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        while (cond.evaluate(env) != false) {
            expr.evaluate(env)
        }
        return false
    }
}