package nodes

import Environment

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val l = list.evaluate(env) as? Indexable
            ?: throw IllegalArgumentException("Access index of non-indexable type")
        val i = index.evaluate(env)
        return l.get(i)
    }
}