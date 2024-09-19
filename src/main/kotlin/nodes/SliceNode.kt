package nodes

import CompilerContext
import Environment
import vm2.operations.Push
import vm2.operations.Slice

data class SliceNode(
    val listOf: List<Node>,
    val type: Type,
) : Node() {

    private val value = ArrayList<Value<*>>()

    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        value.clear()
        listOf.forEach { node ->
            when (val v = node.evaluate(env)) {
                is Collection<*> -> value.addAll(listOf(v))
                else -> value.add(v)
            }
        }
        return SliceValue(value)
    }

    override fun compile(ctx: CompilerContext): Type {
        listOf.forEach { it.compile(ctx) }
        ctx.add(Push(listOf.size))
        ctx.add(Slice())
        return SliceType(type)
    }

}

data class SliceType(val derived: Type) : Type {
    override val type: BaseType
        get() = BaseType.SLICE

    override fun default(ctx: CompilerContext) {
        ctx.add(Push(value = 0))
    }
}

class SliceValue(val list: List<Value<*>>) : Value<List<Value<*>>>(list), Indexable {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "size" -> IntValue(list.size)
            "sub" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0].toInt()
                val end = args[1].toInt()
                SliceValue(list.subList(start, end))
            }

            "map" -> {
                if (args?.size != 1 || args[0] !is FuncValue) {
                    throw IllegalArgumentException("Property 'map' requires one func argument")
                }
                val func = args[0] as FuncValue
                val result = list.mapIndexed { index, item ->
                    func.run(args = listOf(IntValue(index), item), it = this)
                }
                SliceValue(result)
            }

            "forEach" -> {
                if (args?.size != 1 || args[0] !is FuncValue) {
                    throw IllegalArgumentException("Property 'forEach' requires one func argument")
                }
                val func = args[0] as FuncValue
                list.forEach { item ->
                    func.run(args = listOf(item), it = this)
                }
                VoidValue()
            }

            "forEachIndexed" -> {
                if (args?.size != 1 || args[0] !is FuncValue) {
                    throw IllegalArgumentException("Property 'forEachIndexed' requires one func argument")
                }
                val func = args[0] as FuncValue
                list.forEachIndexed { index, item ->
                    func.run(args = listOf(IntValue(index), item), it = this)
                }
                VoidValue()
            }

            "reversed" -> {
                if (args?.size != 0) {
                    throw IllegalArgumentException("Property 'reversed' requires no arguments")
                }
                val result = list.reversed()
                SliceValue(result)
            }

            "reduce" -> {
                if (args?.size != 1 || args[0] !is FuncValue) {
                    throw IllegalArgumentException("Property 'reduce' requires one func argument")
                }
                val func = args[0] as FuncValue
                val result = list.reduce { acc, item ->
                    func.run(args = listOf(acc, item), it = this)
                }
                result
            }

            "plus" -> {
                if (args == null) {
                    throw IllegalArgumentException("Property 'plus' requires at least one argument")
                }
                SliceValue(list.plus(args))
            }

            else -> super.property(name, args)
        }
    }

    override fun get(key: Value<*>): Value<*> {
        return list[key.toInt()]
    }
}
