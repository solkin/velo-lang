package nodes

import Environment

data class ListNode(
    val listOf: List<Node>,
) : Node() {

    private val value = ArrayList<Type<*>>()

    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        value.clear()
        listOf.forEach { node ->
            when (val v = node.evaluate(env)) {
                is Collection<*> -> value.addAll(listOf(v))
                else -> value.add(v)
            }
        }
        return ListType(value)
    }
}

class ListType(val value: List<Type<*>>) : Type<List<Type<*>>>(value)
