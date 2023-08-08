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

            "map" -> {
                if (args?.size != 1 || args[0] !is LambdaType) {
                    throw IllegalArgumentException("Property 'map' requires one lambda argument")
                }
                val lambda = args[0] as LambdaType
                val result = value.map { item ->
                    lambda.run(args = listOf(item), it = this)
                }
                ListType(result)
            }

            "reduce" -> {
                if (args?.size != 1 || args[0] !is LambdaType) {
                    throw IllegalArgumentException("Property 'reduce' requires one lambda argument")
                }
                val lambda = args[0] as LambdaType
                val result = value.reduce { acc, item ->
                    lambda.run(args = listOf(acc, item), it = this)
                }
                result
            }

            else -> super.property(name, args)
        }
    }
}
