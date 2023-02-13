package nodes

import Environment

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        var v: Any = false
        prog.forEach { v = it.evaluate(env) }
        return v
    }
}