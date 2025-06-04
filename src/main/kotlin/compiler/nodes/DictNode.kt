package compiler.nodes

import compiler.Context
import vm.operations.ArrLen
import vm.operations.ArrSet
import vm.operations.DictArr
import vm.operations.DictDel
import vm.operations.DictKey
import vm.operations.DictKeys
import vm.operations.DictLen
import vm.operations.DictOf
import vm.operations.DictSet
import vm.operations.DictVal
import vm.operations.DictVals
import vm.operations.Push

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
        ctx.add(Push(dictOf.size))
        ctx.add(DictOf())
        return DictType(PairType(keyType, valType))
    }
}

data class DictType(val derived: PairType) : Type {
    override fun sameAs(type: Type): Boolean {
        return type is DictType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "set" -> DictSetProp
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

    override fun log() = toString()
}

object DictLenProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(DictLen())
        return IntType
    }
}

object DictKeysProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictKeys())
        return ArrayType(derived = type.derived.first)
    }
}

object DictValsProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictVals())
        return ArrayType(derived = type.derived.second)
    }
}

object DictKeyProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictKey())
        return BoolType
    }
}

object DictValProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictVal())
        return BoolType
    }
}

object DictSetProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictSet())
        return VoidType
    }
}

object DictDelProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictDel())
        return BoolType
    }
}

object DictArrProp : Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as DictType
        ctx.add(DictArr())
        return ArrayType(type.derived)
    }
}
