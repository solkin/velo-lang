package compiler.nodes

import compiler.Context
import vm.operations.ArrLen
import vm.operations.ArrSet
import vm.operations.DictArr
import vm.operations.DictDel
import vm.operations.DictKeys
import vm.operations.DictLen
import vm.operations.DictOf
import vm.operations.DictSet
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
            if (itemKeyType.type != keyType.type) {
                throw Exception("Dict element key \"$k\" type ${itemKeyType.type} is differ from declared type ${keyType.type}")
            }
            val itemValType = v.compile(ctx)
            if (itemValType.type != valType.type) {
                throw Exception("Dict element value \"$v\" type ${itemValType.type} is differ from declared type ${valType.type}")
            }
        }
        ctx.add(Push(dictOf.size))
        ctx.add(DictOf())
        return DictType(PairType(keyType, valType))
    }
}

data class DictType(val derived: PairType) : Type {
    override val type: BaseType
        get() = BaseType.DICT

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
            "arr" -> DictArrProp
            else -> null
        }
    }
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
