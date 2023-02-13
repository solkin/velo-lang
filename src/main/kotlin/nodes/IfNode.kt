package nodes

import Environment

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        val cond = condNode.evaluate(env)
        if (cond != false) return thenNode.evaluate(env)
        return elseNode?.let { elseNode.evaluate(env) } ?: false
    }
}