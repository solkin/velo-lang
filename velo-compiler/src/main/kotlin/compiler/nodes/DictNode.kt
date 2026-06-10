package compiler.nodes

import core.Op

import compiler.Context

data class DictNode(
    val dictOf: Map<Node, Node>,
    val keyType: Type,
    val valType: Type,
) : Node() {
    override fun compile(ctx: Context): Type {
        dictOf.forEach { (k, v) ->
            val itemKeyType = k.compile(ctx)
            if (!itemKeyType.sameAs(keyType)) {
                throw Exception("Dict element key \"$k\" type ${itemKeyType.log()} is differ from declared type ${keyType.log()}")
            }
            val itemValType = v.compile(ctx)
            if (!itemValType.sameAs(valType)) {
                throw Exception("Dict element value \"$v\" type ${itemValType.log()} is differ from declared type ${valType.log()}")
            }
        }
        ctx.add(Op.Push(dictOf.size))
        ctx.add(Op.DictOf)
        return DictType(TupleType(listOf(keyType, valType)))
    }
}

data class DictType(val derived: TupleType) : IndexAssignable {
    override fun sameAs(type: Type): Boolean {
        return type is DictType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Op.Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "del" -> DictDelProp
            "len" -> DictLenProp
            "keys" -> DictKeysProp
            "vals" -> DictValsProp
            "key" -> DictKeyProp
            "val" -> DictValProp
            "arr" -> DictArrProp
            else -> null
        }
    }

    override fun log() = "dict[${derived.types.joinToString(":") { it.log() }}]"

    override fun vmType() = core.VmType.Dict(
        keyType = derived.types.first().vmType(),
        valueType = derived.types.second().vmType()
    )

    override fun compileIndex(ctx: Context): Type {
        ctx.add(Op.DictIndex)
        return derived.types.second()
    }

    override fun compileAssignment(ctx: Context) {
        ctx.add(Op.DictSet)
    }

    override fun name() = "dict"
}

object DictLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(Op.DictLen)
        return IntType
    }
}

object DictKeysProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(Op.DictKeys)
        return ArrayType(derived = type.derived.types.first())
    }
}

object DictValsProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(Op.DictVals)
        return ArrayType(derived = type.derived.types.second())
    }
}

object DictKeyProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(Op.DictKey)
        return BoolType
    }
}

object DictValProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(Op.DictVal)
        return BoolType
    }
}

object DictDelProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(Op.DictDel)
        return BoolType
    }
}

object DictArrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(Op.DictArr)
        return ArrayType(type.derived)
    }
}

private fun List<Type>.second(): Type {
    return this[1]
}
