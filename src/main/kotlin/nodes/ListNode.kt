package nodes

import Environment

data class ListNode(
    val listOf: List<Node>,
) : Node() {

    private val value = ArrayList<Any>()

    override fun evaluate(env: Environment<Any>): Any {
        value.clear()
        listOf.forEach { node ->
            when(val v = node.evaluate(env)) {
                is Collection<*> -> value.addAll(listOf(v))
                else -> value.add(v)
            }
        }
        return value
    }
}