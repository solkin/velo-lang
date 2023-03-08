package nodes

import Environment

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        val l = list.evaluate(env) as? List<*>
            ?: throw IllegalArgumentException("Access index of non-list node")
        val i = index.evaluate(env).toString().toDoubleOrNull()?.toInt()
            ?: throw IllegalArgumentException("Expecting an integer index element")
        return l[i] ?: false
    }
}