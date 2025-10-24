package compiler.nodes

import compiler.Context
import vm.Operation
import vm.operations.ArrCon
import vm.operations.ArrLen
import vm.operations.Call
import vm.operations.Store
import vm.operations.Dup
import vm.operations.Load
import vm.operations.If
import vm.operations.More
import vm.operations.Move
import vm.operations.Add
import vm.operations.ArrCopy
import vm.operations.ArrLoad
import vm.operations.ArrNew
import vm.operations.ArrStore
import vm.operations.Pop
import vm.operations.Push
import vm.operations.Sub

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
        // Create new array
        ctx.add(Push(value = listOf.size))
        ctx.add(ArrNew())
        // Store items to the new array
        ctx.add(Push(value = 0))
        ctx.add(Push(value = listOf.size))
        ctx.add(ArrStore())
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
            "sub" -> ArraySubProp
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
        ctx.add(Pop()) // Pop array from stack
    }

    override fun name() = "array"
}

object ArrayLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(ArrLen())
        return IntType
    }
}

object ArraySubProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType

        if (args.size != 2) throw Exception("Property 'sub' requires two int arguments: from and till")

        val fromVal = ctx.def(name = "@from", type = IntType)
        ctx.add(Store(fromVal.index))

        val tillVal = ctx.def(name = "@till", type = IntType)
        ctx.add(Store(tillVal.index))

        val srcVal = ctx.def(name = "@src", type = type)
        ctx.add(Store(srcVal.index))

        // Calculate length
        ctx.add(Load(tillVal.index))
        ctx.add(Load(fromVal.index))
        ctx.add(Sub())
        ctx.add(Dup()) // Duplicate value on stack
        // Store length
        val lengthVal = ctx.def(name = "@length", type = IntType)
        ctx.add(Store(lengthVal.index))

        // Create new array
        ctx.add(ArrNew())
        val dstVal = ctx.def(name = "@dst", type = type)
        ctx.add(Store(dstVal.index))

        // Load params for copy
        ctx.add(Load(dstVal.index)) // Destination array
        ctx.add(Load(srcVal.index)) // Source array
        ctx.add(Load(lengthVal.index)) // Length to copy
        ctx.add(Push(value = 0)) // Destination position
        ctx.add(Load(fromVal.index)) // Source position

        // Copy array
        ctx.add(ArrCopy())

        // Put new array on stack
        ctx.add(Load(dstVal.index))

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

        val addVal = ctx.def(name = "@add", type = args.first())
        ctx.add(Store(addVal.index))

        val srcVal = ctx.def(name = "@src", type = type)
        ctx.add(Store(srcVal.index))

        // Calculate new array len
        ctx.add(Load(srcVal.index))
        ctx.add(ArrLen())
        // ... and store old length
        ctx.add(Dup())
        val lengthVal = ctx.def(name = "@length", type = IntType)
        ctx.add(Store(lengthVal.index))
        // ... continue
        ctx.add(Push(value = 1))
        ctx.add(Add())

        // Create new array with calculated length
        ctx.add(ArrNew())
        val dstVal = ctx.def(name = "@dst", type = type)
        ctx.add(Store(dstVal.index))

        // Copy src array to dst
        ctx.add(Load(dstVal.index)) // Destination array
        ctx.add(Load(srcVal.index)) // Source array
        ctx.add(Load(lengthVal.index)) // Length to copy
        ctx.add(Push(value = 0)) // Destination position
        ctx.add(Push(value = 0)) // Source position
        ctx.add(ArrCopy()) // Copy

        // Add the last item
        ctx.add(Load(addVal.index)) // Element to put
        ctx.add(Load(dstVal.index)) // Where to put
        ctx.add(Load(lengthVal.index)) // Index to put
        ctx.add(Push(value = 1)) // Elements count
        ctx.add(ArrStore())

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

        // Create new resulting array
        ctx.add(Load(size.index))
        ctx.add(ArrNew())

        // Store items into newly created array
        ctx.add(Push(value = 0)) // ArrStore index to store items
        ctx.add(Load(size.index)) // ArrStore index to store items
        ctx.add(ArrStore())

        return ArrayType(arg.derived)
    }
}
