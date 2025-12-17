package compiler.nodes

import compiler.Context
import vm.operations.ArrLoad
import vm.operations.ArrNew
import vm.operations.ArrStore
import vm.operations.Pop
import vm.operations.Push

data class TupleNode(
    val entries: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (entries.isEmpty()) throw IllegalArgumentException("Tuple can't be empty")
        val types = entries.map { it.compile(ctx) }
        val type = TupleType(types)
        // Create new array
        ctx.add(Push(value = entries.size))
        ctx.add(ArrNew())
        // Store items to the new array
        ctx.add(Push(value = 0))
        ctx.add(Push(value = entries.size))
        ctx.add(ArrStore())
        return type
    }
}

data class TupleType(val types: List<Type>) : Type {
    override fun sameAs(type: Type): Boolean {
        return type is TupleType &&
                type.types.size == types.size &&
                type.types.filterIndexed { i, type -> !type.sameAs(types[i]) }.isEmpty()
    }

    override fun default(ctx: Context) {
        TODO("not implemented")
    }

    override fun prop(name: String): Prop {
        val index = name.toIntOrNull() ?: throw IllegalArgumentException("Unsupported tuple prop '$name'")
        return TupleEntryProp(index = index - 1) // Minus 1 due to props has human-agreeable format 1+ instead of 0+
    }

    override fun log() = toString()

    override fun vmType() = vm.VmTuple(types.map { it.vmType() })

    override fun name() = "tuple"
}

data class TupleEntryProp(val index: Int) : AssignableProp {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? TupleType ?: throw IllegalArgumentException("Tuple operation on non-tuple type $type")
        ctx.add(Push(value = index))
        ctx.add(Push(value = 1)) // ArrLoad count
        ctx.add(ArrLoad())
        return type.types[index]
    }

    override fun compileAssignment(
        parentType: Type,
        assignType: Type,
        args: List<Type>,
        ctx: Context
    ) {
        parentType as? TupleType ?: throw IllegalArgumentException("Tuple operation on non-tuple type $parentType")
        if (!parentType.types[index].sameAs(assignType)) {
            throw IllegalArgumentException("Cannot assign type $assignType to tuple $index of type ${parentType.types[index]}")
        }
        ctx.add(Push(value = index))
        ctx.add(Push(value = 1)) // ArrStore count
        ctx.add(ArrStore())
        ctx.add(Pop()) // Pop array from stack
    }
}
