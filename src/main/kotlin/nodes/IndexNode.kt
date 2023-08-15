package nodes

import Environment

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val l = list.evaluate(env) as? ListType
            ?: throw IllegalArgumentException("Access index of non-list node")
        val i = index.evaluate(env).toInt()
        return l.value[i]
    }
}