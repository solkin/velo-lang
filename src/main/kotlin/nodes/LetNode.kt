package nodes

import Environment

data class LetNode(
    val name: String,
    val vars: List<String>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        TODO("Not yet implemented")
    }
}