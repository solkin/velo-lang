package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.Operation
import vm.operations.ArrCon
import vm.operations.Call
import vm.operations.Def
import vm.operations.Dup
import vm.operations.Ext
import vm.operations.Free
import vm.operations.Get
import vm.operations.If
import vm.operations.Index
import vm.operations.Less
import vm.operations.Move
import vm.operations.Plus
import vm.operations.Push
import vm.operations.ArrOf
import vm.operations.ArrLen
import vm.operations.ArrPlus
import vm.operations.SubArr
import vm.operations.Set

data class ArrayNode(
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
        return ArrayValue(value)
    }

    override fun compile(ctx: Context): Type {
        listOf.forEach { it.compile(ctx) }
        ctx.add(Push(listOf.size))
        ctx.add(ArrOf())
        return ArrayType(type)
    }

}

data class ArrayType(val derived: Type) : Type {
    override val type: BaseType
        get() = BaseType.ARRAY

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }
}

object ArrayLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(ArrLen())
        return IntType
    }
}

object SubArrayProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        ctx.add(SubArr())
        return ArrayType(type.derived)
    }
}

object ArrayConProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        if (args.size != 1 && type != args.first()) throw Exception("Property 'con' requires same type array as argument")
        ctx.add(ArrCon())
        return ArrayType(type.derived)
    }
}

object ArrayPlusProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        if (args.isEmpty()) throw Exception("Property 'plus' requires at least one argument")
        if (args.find { it.type != type.derived.type } != null) {
            throw Exception("Property 'plus' arguments must be array-typed")
        }
        ctx.add(ArrPlus())
        return ArrayType(type.derived)
    }
}

object MapArrayProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        val arg = args.first() as FuncType

        ctx.add(Ext())
        ctx.enumerator.extend()

        val func = ctx.enumerator.def(name = "@func", type = arg)
        ctx.add(Def(func.index))

        ctx.add(Dup())
        ctx.add(ArrLen())
        val size = ctx.enumerator.def(name = "@size", type = IntType)
        ctx.add(Def(size.index))

        ctx.add(Push(0))
        val i = ctx.enumerator.def(name = "@i", type = IntType)
        ctx.add(Def(i.index))

        val array = ctx.enumerator.def(name = "@array", type = ArrayType(arg.derived))
        ctx.add(Def(array.index))

        val condCtx: MutableList<Operation> = ArrayList()
        with(condCtx) {
            add(Get(i.index))
            add(Get(size.index))
            add(Less())
        }

        val exprCtx: MutableList<Operation> = ArrayList()
        with(exprCtx) {
            // index
            add(Get(i.index))
            // item
            add(Get(array.index))
            add(Get(i.index))
            add(Index())
            // func
            add(Get(func.index))
            // call func
            add(Call())
            // increment i
            add(Get(i.index))
            add(Push(1))
            add(Plus())
            add(Set(i.index))
        }
        exprCtx.add(Move(-(exprCtx.size + condCtx.size + 2))) // +2 because to move and if is not included

        ctx.addAll(condCtx)
        ctx.add(If(exprCtx.size))
        ctx.addAll(exprCtx)

        ctx.add(Get(size.index))
        ctx.add(ArrOf())

        ctx.add(Free())
        ctx.enumerator.free()

        return ArrayType(type.derived)
    }
}

class ArrayValue(val list: List<Value<*>>) : Value<List<Value<*>>>(list), Indexable {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "size" -> IntValue(list.size)
            "sub" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0].toInt()
                val end = args[1].toInt()
                ArrayValue(list.subList(start, end))
            }

            "con" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'con' requires array as argument")
                }
                val arr = args[0] as ArrayValue
                ArrayValue(list + arr.list)
            }

            "map" -> {
                if (args?.size != 1 || args[0] !is FuncValue) {
                    throw IllegalArgumentException("Property 'map' requires one func argument")
                }
                val func = args[0] as FuncValue
                val result = list.mapIndexed { index, item ->
                    func.run(args = listOf(IntValue(index), item), it = this)
                }
                ArrayValue(result)
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
                ArrayValue(result)
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
                ArrayValue(list.plus(args))
            }

            else -> super.property(name, args)
        }
    }

    override fun get(key: Value<*>): Value<*> {
        return list[key.toInt()]
    }
}
