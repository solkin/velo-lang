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

class ListType(val list: List<Type<*>>) : Type<List<Type<*>>>(list), Indexable {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "len" -> IntType(list.size)
            "sub" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0].toInt()
                val end = args[1].toInt()
                ListType(list.subList(start, end))
            }

            "map" -> {
                if (args?.size != 1 || args[0] !is FuncType) {
                    throw IllegalArgumentException("Property 'map' requires one func argument")
                }
                val func = args[0] as FuncType
                val result = list.mapIndexed { index, item ->
                    func.run(args = listOf(IntType(index), item), it = this)
                }
                ListType(result)
            }

            "reduce" -> {
                if (args?.size != 1 || args[0] !is FuncType) {
                    throw IllegalArgumentException("Property 'reduce' requires one func argument")
                }
                val func = args[0] as FuncType
                val result = list.reduce { acc, item ->
                    func.run(args = listOf(acc, item), it = this)
                }
                result
            }

            "plus" -> {
                if (args == null) {
                    throw IllegalArgumentException("Property 'plus' requires at least one argument")
                }
                ListType(list.plus(args))
            }

            else -> super.property(name, args)
        }
    }

    override fun get(key: Type<*>): Type<*> {
        return list[key.toInt()]
    }
}
