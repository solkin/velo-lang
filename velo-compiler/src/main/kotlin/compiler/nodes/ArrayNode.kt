package compiler.nodes

import compiler.Context
import core.Op

data class ArrayNode(
    val listOf: List<Node>?,
    val length: Node?,
    val type: Type,
) : Node() {
    override fun compile(ctx: Context): Type {
        return when {
            listOf != null -> {
                listOf.forEach {
                    val itemType = it.compile(ctx)
                    if (!assignableArg(type, itemType)) {
                        throw Exception("Array element \"$it\" type ${itemType.log()} is differ from array type ${type.log()}")
                    }
                }
                // Create new array
                ctx.add(Op.Push(value = listOf.size))
                ctx.add(Op.ArrNew)
                // Store items to the new array
                ctx.add(Op.Push(value = 0))
                ctx.add(Op.Push(value = listOf.size))
                ctx.add(Op.ArrStore)
                ArrayType(derived = type)
            }

            length != null -> {
                val lengthType = length.compile(ctx)
                if (!lengthType.sameAs(IntType)) {
                    throw Exception("Array length must be int, but \"$lengthType\" type is provided")
                }
                ctx.add(Op.ArrNew)
                ArrayType(derived = type)
            }

            else -> throw IllegalStateException("Length or initialization items must be provided to allocate array")
        }
    }
}

data class ArrayType(val derived: Type) : IndexAssignable {
    override fun sameAs(type: Type): Boolean {
        return type is ArrayType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = 0))
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

    override fun log() = "array[${derived.log()}]"

    override fun vmType() = core.VmType.Array(derived.vmType())

    override fun compileIndex(ctx: Context): Type {
        ctx.add(Op.Push(value = 1)) // ArrLoad count
        ctx.add(Op.ArrLoad)
        return derived
    }

    override fun compileAssignment(ctx: Context) {
        ctx.add(Op.Push(value = 1)) // ArrStore count
        ctx.add(Op.ArrStore)
        ctx.add(Op.Pop) // Pop array from stack
    }

    override fun name() = "array"
}

object ArrayLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.ArrLen)
        return IntType
    }
}

object ArraySubProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType

        if (args.size != 2) throw Exception("Property 'sub' requires two int arguments: from and till")

        val fromVal = ctx.def(name = "@from", type = IntType)
        ctx.add(Op.Store(fromVal.index))

        val tillVal = ctx.def(name = "@till", type = IntType)
        ctx.add(Op.Store(tillVal.index))

        val srcVal = ctx.def(name = "@src", type = type)
        ctx.add(Op.Store(srcVal.index))

        // Calculate length
        ctx.add(Op.Load(tillVal.index))
        ctx.add(Op.Load(fromVal.index))
        ctx.add(Op.Sub)
        ctx.add(Op.Dup) // Duplicate value on stack
        // Store length
        val lengthVal = ctx.def(name = "@length", type = IntType)
        ctx.add(Op.Store(lengthVal.index))

        // Create new array
        ctx.add(Op.ArrNew)
        val dstVal = ctx.def(name = "@dst", type = type)
        ctx.add(Op.Store(dstVal.index))

        // Load params for copy
        ctx.add(Op.Load(dstVal.index)) // Destination array
        ctx.add(Op.Load(srcVal.index)) // Source array
        ctx.add(Op.Load(lengthVal.index)) // Length to copy
        ctx.add(Op.Push(value = 0)) // Destination position
        ctx.add(Op.Load(fromVal.index)) // Source position

        // Copy array
        ctx.add(Op.ArrCopy)

        // Put new array on stack
        ctx.add(Op.Load(dstVal.index))

        return ArrayType(type.derived)
    }
}

object ArrayConProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        if (args.size != 1 && type != args.first()) throw Exception("Property 'con' requires same type array as argument")

        val bVal = ctx.def(name = "@b", type = type)
        ctx.add(Op.Store(bVal.index))
        val aVal = ctx.def(name = "@a", type = type)
        ctx.add(Op.Store(aVal.index))
        // Calculate target length
        ctx.add(Op.Load(aVal.index))
        ctx.add(Op.ArrLen)
        ctx.add(Op.Dup)
        val alenVal = ctx.def(name = "@alen", type = IntType)
        ctx.add(Op.Store(alenVal.index))
        ctx.add(Op.Load(bVal.index))
        ctx.add(Op.ArrLen)
        ctx.add(Op.Add)
        // Create destination array
        ctx.add(Op.ArrNew)
        val dstVal = ctx.def(name = "@dst", type = type)
        ctx.add(Op.Store(dstVal.index))
        // Copy first array to dst
        ctx.add(Op.Load(dstVal.index)) // Destination array
        ctx.add(Op.Load(aVal.index)) // Source array
        ctx.add(Op.Load(alenVal.index)) // Length to copy
        ctx.add(Op.Push(value = 0)) // Destination position
        ctx.add(Op.Push(value = 0)) // Source position
        ctx.add(Op.ArrCopy) // Copy first
        // Copy second array to dst
        ctx.add(Op.Load(dstVal.index)) // Destination array
        ctx.add(Op.Load(bVal.index)) // Source array
        ctx.add(Op.Load(bVal.index)) // Second array for length (next operation)
        ctx.add(Op.ArrLen) // Length to copy
        ctx.add(Op.Load(alenVal.index)) // Destination position
        ctx.add(Op.Push(value = 0)) // Source position
        ctx.add(Op.ArrCopy) // Copy second

        // Finally load newly created array
        ctx.add(Op.Load(dstVal.index))

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
        ctx.add(Op.Store(addVal.index))

        val srcVal = ctx.def(name = "@src", type = type)
        ctx.add(Op.Store(srcVal.index))

        // Calculate new array len
        ctx.add(Op.Load(srcVal.index))
        ctx.add(Op.ArrLen)
        // ... and store old length
        ctx.add(Op.Dup)
        val lengthVal = ctx.def(name = "@length", type = IntType)
        ctx.add(Op.Store(lengthVal.index))
        // ... continue
        ctx.add(Op.Push(value = 1))
        ctx.add(Op.Add)

        // Create new array with calculated length
        ctx.add(Op.ArrNew)
        val dstVal = ctx.def(name = "@dst", type = type)
        ctx.add(Op.Store(dstVal.index))

        // Copy src array to dst
        ctx.add(Op.Load(dstVal.index)) // Destination array
        ctx.add(Op.Load(srcVal.index)) // Source array
        ctx.add(Op.Load(lengthVal.index)) // Length to copy
        ctx.add(Op.Push(value = 0)) // Destination position
        ctx.add(Op.Push(value = 0)) // Source position
        ctx.add(Op.ArrCopy) // Copy

        // Add the last item
        ctx.add(Op.Load(addVal.index)) // Element to put
        ctx.add(Op.Load(dstVal.index)) // Where to put
        ctx.add(Op.Load(lengthVal.index)) // Index to put
        ctx.add(Op.Push(value = 1)) // Elements count
        ctx.add(Op.ArrStore)

        return ArrayType(type.derived)
    }
}

object MapArrayProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as ArrayType
        val arg = args.first() as FuncType

        // Callback is `func(T value)` or `func(T value, int index)` — value
        // first, index optional. (Index second so the common case stays short.)
        // A loose `func[U]` (no declared params) keeps both, the back-compatible
        // shape, since its arity isn't visible here.
        val params = arg.args
        if (params != null && params.size != 1 && params.size != 2) {
            throw IllegalArgumentException("map callback takes (value) or (value, index), got ${params.size} parameters")
        }
        val withIndex = params == null || params.size == 2

        val func = ctx.def(name = "@func", type = arg)
        ctx.add(Op.Store(func.index))

        ctx.add(Op.Dup)
        ctx.add(Op.ArrLen)
        val size = ctx.def(name = "@size", type = IntType)
        ctx.add(Op.Store(size.index))

        ctx.add(Op.Push(0))
        val i = ctx.def(name = "@i", type = IntType)
        ctx.add(Op.Store(i.index))

        val array = ctx.def(name = "@array", type = ArrayType(arg.derived))
        ctx.add(Op.Store(array.index))

        val condCtx: MutableList<Op> = ArrayList()
        with(condCtx) {
            add(Op.Load(size.index))
            add(Op.Load(i.index))
            add(Op.More)
        }

        val exprCtx: MutableList<Op> = ArrayList()
        with(exprCtx) {
            // value (first parameter)
            add(Op.Load(array.index))
            add(Op.Load(i.index))
            add(Op.Push(value = 1)) // ArrLoad count
            add(Op.ArrLoad)
            // index (optional second parameter)
            if (withIndex) {
                add(Op.Load(i.index))
            }
            // func
            add(Op.Load(func.index))
            // call func
            add(Op.Call(args = if (withIndex) 2 else 1))
            // increment i
            add(Op.Load(i.index))
            add(Op.Push(1))
            add(Op.Add)
            add(Op.Store(i.index))
        }
        exprCtx.add(Op.Move(-(exprCtx.size + condCtx.size + 2))) // +2 because to move and if is not included

        ctx.addAll(condCtx)
        ctx.add(Op.If(exprCtx.size))
        ctx.addAll(exprCtx)

        // Create new resulting array
        ctx.add(Op.Load(size.index))
        ctx.add(Op.ArrNew)

        // Store items into newly created array
        ctx.add(Op.Push(value = 0)) // ArrStore index to store items
        ctx.add(Op.Load(size.index)) // ArrStore index to store items
        ctx.add(Op.ArrStore)

        return ArrayType(arg.derived)
    }
}
