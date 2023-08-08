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

class ListType(val value: List<Type<*>>) : Type<List<Type<*>>>(value) {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "len" -> NumType(value.size.toDouble())
            "sub" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0] as NumType
                val end = args[1] as NumType
                ListType(value.subList(start.value.toInt(), end.value.toInt()))
            }

            else -> super.property(name, args)
        }
    }
}
