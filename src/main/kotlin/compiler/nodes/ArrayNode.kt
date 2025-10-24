package compiler.nodes

import compiler.Context
import vm.Operation
import vm.operations.ArrCon
import vm.operations.ArrLen
import vm.operations.ArrOf
import vm.operations.Call
import vm.operations.Store
import vm.operations.Dup
import vm.operations.Load
import vm.operations.If
import vm.operations.More
import vm.operations.Move
import vm.operations.Add
import vm.operations.ArrLoad
import vm.operations.ArrStore
import vm.operations.Push
import vm.operations.ArrSub

data class ArrayNode(
    val listOf: List<Node>,
    val type: Type,
) : Node() {
    override fun compile(ctx: Context): Type {
        listOf.forEach {
            val itemType = it.compile(ctx)
            if (!itemType.sameAs(type)) {
                throw Exception("Array element \"$it\" type ${itemType.log()} is differ from array type ${type.log()}")
            }
        }
        ctx.add(Push(listOf.size))
        ctx.add(ArrOf())
        return ArrayType(type)
    }
}

data class ArrayType(val derived: Type) : IndexAssignable {
    override fun sameAs(type: Type): Boolean {
        return type is ArrayType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "sub" -> SubArrayProp
            "len" -> ArrayLenProp
            "con" -> ArrayConProp
            "plus" -> ArrayPlusProp
            "map" -> MapArrayProp
            else -> null
        }
    }

    override fun log() = toString()

    override fun vmType() = vm.VmArray()

    override fun compileIndex(ctx: Context): Type {
        ctx.add(Push(value = 1)) // ArrLoad count
        ctx.add(ArrLoad())
        return derived
    }

    override fun compileAssignment(ctx: Context) {
        ctx.add(Push(value = 1)) // ArrStore count
        ctx.add(ArrStore())
    }

    override fun name() = "array"
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
        ctx.add(ArrSub())
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
        if (args.find { !it.sameAs(type.derived) } != null) {
            throw Exception("Property 'plus' arguments must be array-typed")
        }
        ctx.add(Push(value = 1)) // array length
        ctx.add(ArrOf()) // create new one-item array
        ctx.add(ArrCon()) // concat arrays
        return ArrayType(type.derived)
    }
}

object MapArrayProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        val arg = args.first() as FuncType

        val func = ctx.def(name = "@func", type = arg)
        ctx.add(Store(func.index))

        ctx.add(Dup())
        ctx.add(ArrLen())
        val size = ctx.def(name = "@size", type = IntType)
        ctx.add(Store(size.index))

        ctx.add(Push(0))
        val i = ctx.def(name = "@i", type = IntType)
        ctx.add(Store(i.index))

        val array = ctx.def(name = "@array", type = ArrayType(arg.derived))
        ctx.add(Store(array.index))

        val condCtx: MutableList<Operation> = ArrayList()
        with(condCtx) {
            add(Load(size.index))
            add(Load(i.index))
            add(More())
        }

        val exprCtx: MutableList<Operation> = ArrayList()
        with(exprCtx) {
            // index
            add(Load(i.index))
            // item
            add(Load(array.index))
            add(Load(i.index))
            add(Push(value = 1)) // ArrLoad count
            add(ArrLoad())
            // func
            add(Load(func.index))
            // call func
            add(Call(args = 2))
            // increment i
            add(Load(i.index))
            add(Push(1))
            add(Add())
            add(Store(i.index))
        }
        exprCtx.add(Move(-(exprCtx.size + condCtx.size + 2))) // +2 because to move and if is not included

        ctx.addAll(condCtx)
        ctx.add(If(exprCtx.size))
        ctx.addAll(exprCtx)

        ctx.add(Load(size.index))
        ctx.add(ArrOf())

        return ArrayType(arg.derived)
    }
}
