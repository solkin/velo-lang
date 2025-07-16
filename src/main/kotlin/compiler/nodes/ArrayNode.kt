package compiler.nodes

import compiler.Context
import vm.Operation
import vm.operations.ArrCon
import vm.operations.ArrLen
import vm.operations.ArrOf
import vm.operations.ArrPlus
import vm.operations.ArrSet
import vm.operations.Call
import vm.operations.Set
import vm.operations.Dup
import vm.operations.Get
import vm.operations.If
import vm.operations.ArrIndex
import vm.operations.Less
import vm.operations.Move
import vm.operations.Plus
import vm.operations.Push
import vm.operations.SubArr

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

data class ArrayType(val derived: Type) : Indexable {
    override fun sameAs(type: Type): Boolean {
        return type is ArrayType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "set" -> SetArrayProp
            "sub" -> SubArrayProp
            "len" -> ArrayLenProp
            "con" -> ArrayConProp
            "plus" -> ArrayPlusProp
            "map" -> MapArrayProp
            else -> null
        }
    }

    override fun log() = toString()

    override fun vmType() = vm.ARRAY

    override fun compileIndex(ctx: Context): Type {
        ctx.add(ArrIndex())
        return derived
    }
}

object ArrayLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(ArrLen())
        return IntType
    }
}

object SetArrayProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        ctx.add(ArrSet())
        return VoidType
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
        if (args.find { !it.sameAs(type.derived) } != null) {
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

        val func = ctx.def(name = "@func", type = arg)
        ctx.add(Set(func.index))

        ctx.add(Dup())
        ctx.add(ArrLen())
        val size = ctx.def(name = "@size", type = IntType)
        ctx.add(Set(size.index))

        ctx.add(Push(0))
        val i = ctx.def(name = "@i", type = IntType)
        ctx.add(Set(i.index))

        val array = ctx.def(name = "@array", type = ArrayType(arg.derived))
        ctx.add(Set(array.index))

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
            add(ArrIndex())
            // func
            add(Get(func.index))
            // call func
            add(Call(args = 2))
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

        return ArrayType(arg.derived)
    }
}
