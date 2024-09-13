package nodes

import Environment
import vm2.Operation
import vm2.operations.*

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

    override fun compile(ops: MutableList<Operation>) {
        listOf.forEach { it.compile(ops) }
        ops.add(Push(listOf.size))
        ops.add(Slice())
    }

    override fun property(name: String, ops: MutableList<Operation>) {
        when(name) {
            "sub" -> ops.add(SubSlice())
            "size" -> ops.add(SliceLen())
            "map" -> {
                val func = 2
                ops.add(Def(func))

                ops.add(Dup())
                ops.add(SliceLen())
                val size = 1
                ops.add(Def(size))

                ops.add(Push(0))
                val i = 3
                ops.add(Def(i))

                val list = 4
                ops.add(Def(list))

                val condOps: MutableList<Operation> = ArrayList()
                with(condOps) {
                    add(Get(i))
                    add(Get(size))
                    add(Less())
                }

                val exprOps: MutableList<Operation> = ArrayList()
                with(exprOps) {
                    // item
                    add(Get(list))
                    add(Get(i))
                    add(Index())
                    // index
                    add(Get(i))
                    // func
                    add(Get(func))
                    // call func
                    add(Call())
                    // increment i
                    add(Get(i))
                    add(Push(1))
                    add(Plus())
                    add(Set(i))
                }
                exprOps.add(Move(-(exprOps.size + condOps.size + 2))) // +2 because to move and if is not included

                ops.addAll(condOps)
                ops.add(If(exprOps.size))
                ops.addAll(exprOps)

                ops.add(Get(size))
                ops.add(Slice())
            }
            else -> throw IllegalArgumentException("Property $name is not supported")
        }
    }
}

class ListType(val list: List<Type<*>>) : Type<List<Type<*>>>(list), Indexable {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "size" -> IntType(list.size)
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

            "forEach" -> {
                if (args?.size != 1 || args[0] !is FuncType) {
                    throw IllegalArgumentException("Property 'forEach' requires one func argument")
                }
                val func = args[0] as FuncType
                list.forEach { item ->
                    func.run(args = listOf(item), it = this)
                }
                VoidType()
            }

            "forEachIndexed" -> {
                if (args?.size != 1 || args[0] !is FuncType) {
                    throw IllegalArgumentException("Property 'forEachIndexed' requires one func argument")
                }
                val func = args[0] as FuncType
                list.forEachIndexed { index, item ->
                    func.run(args = listOf(IntType(index), item), it = this)
                }
                VoidType()
            }

            "reversed" -> {
                if (args?.size != 0) {
                    throw IllegalArgumentException("Property 'reversed' requires no arguments")
                }
                val result = list.reversed()
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
