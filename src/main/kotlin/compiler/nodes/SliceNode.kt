package compiler.nodes

import CompilerContext
import compiler.Environment
import vm2.Operation
import vm2.operations.Call
import vm2.operations.Def
import vm2.operations.Dup
import vm2.operations.Ext
import vm2.operations.Free
import vm2.operations.Get
import vm2.operations.If
import vm2.operations.Index
import vm2.operations.Less
import vm2.operations.Move
import vm2.operations.Plus
import vm2.operations.Push
import vm2.operations.Slice
import vm2.operations.SliceLen
import vm2.operations.SubSlice
import vm2.operations.Set

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

object SliceLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: CompilerContext): Type {
        ctx.add(SliceLen())
        return IntType
    }
}

object SubSliceProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: CompilerContext): Type {
        type as SliceType
        ctx.add(SubSlice())
        return SliceType(type.derived)
    }
}

object MapSliceProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: CompilerContext): Type {
        type as SliceType
        val arg = args.first() as FuncType

        ctx.add(Ext())

        val func = ctx.defVar("_func", arg)
        ctx.add(Def(func.index))

        ctx.add(Dup())
        ctx.add(SliceLen())
        val size = ctx.defVar("_size", IntType)
        ctx.add(Def(size.index))

        ctx.add(Push(0))
        val i = ctx.defVar("_i", IntType)
        ctx.add(Def(i.index))

        val slice = ctx.defVar("_slice", SliceType(arg.derived))
        ctx.add(Def(slice.index))

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
            add(Get(slice.index))
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
        ctx.add(Slice())

        ctx.add(Free())

        return SliceType(type.derived)
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
