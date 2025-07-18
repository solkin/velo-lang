package compiler.nodes

import compiler.Context
import vm.operations.MakeTuple
import vm.operations.TupleEntryGet
import vm.operations.TupleEntrySet

data class TupleNode(
    val entries: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (entries.isEmpty()) throw IllegalArgumentException("Tuple can't be empty")
        val types = entries.map { it.compile(ctx) }
        ctx.add(MakeTuple(size = entries.size))
        return TupleType(types)
    }
}

data class TupleType(val types: List<Type>) : Type {
    override fun sameAs(type: Type): Boolean {
        return type is TupleType && type.types.filterIndexed { i, type -> !type.sameAs(types[i]) }.isEmpty()
    }

    override fun default(ctx: Context) {
        TODO("not implemented")
    }

    override fun prop(name: String): Prop? {
        val index = name.toIntOrNull() ?: throw IllegalArgumentException("Unsupported tuple prop '$name'")
        return TupleEntryProp(index = index - 1) // Minus 1 due to props has human-agreeable format 1+ instead of 0+
    }

    override fun log() = toString()

    override fun vmType() = vm.TUPLE
}

data class TupleEntryProp(val index: Int) : AssignableProp {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? TupleType ?: throw IllegalArgumentException("Tuple operation on non-tuple type $type")
        ctx.add(TupleEntryGet(index))
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
        ctx.add(TupleEntrySet(index))
    }
}
