package compiler.nodes

import compiler.Context
import vm.operations.Def
import vm.operations.Ext
import vm.operations.Free
import vm.operations.Push
import vm.operations.MakeStruct
import vm.operations.Move
import vm.operations.PairFirst
import vm.operations.Pc
import vm.operations.Plus
import vm.operations.Ret
import vm.operations.StructElement
import vm.operations.SubStr
import java.util.SortedMap
import java.util.TreeMap

data class StructNode(
    val name: String,
    val defs: List<DefNode>,
) : Node() {
    override fun compile(ctx: Context): Type {
        val elements = LinkedHashMap<String, Type>()
        defs.reversed().forEach { def ->
            elements[def.name] = def.type
        }

        val resultType: Type = FuncType(derived = StructType(name, elements))

        // Insert function address to stack
        val defCmdCount = 5
        ctx.add(Pc())
        ctx.add(Push(value = defCmdCount))
        ctx.add(Plus())
        // Define var and move address to var if name is defined
        val v = ctx.enumerator.def(name, resultType)
        ctx.add(Def(v.index))

        // Compile body
        val funcOps = ctx.fork()
        // Compile function body - structure constructor
        funcOps.add(Push(value = elements.size))
        funcOps.add(MakeStruct())
        funcOps.add(Ret())

        // Skip function body
        ctx.add(Move(funcOps.size()))

        // Add function operations to real context
        ctx.merge(funcOps)

        return VoidType
    }
}

data class StructType(val name: String, val elements: Map<String, Type>) : Type {
    override val type: BaseType
        get() = BaseType.STRUCT

    override fun default(ctx: Context) {
        throw Exception("Struct has no default value")
    }

    override fun prop(name: String): Prop? {
        if (elements.containsKey(name)) {
            return StructElementProp(name)
        }
        return null
    }
}

data class StructElementProp(val name: String): Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? StructType ?: throw IllegalArgumentException("Struct operation on non-struct type $type")
        val elementIndex = type.elements.keys.indexOf(name)
        if (elementIndex == -1) {
            throw IllegalArgumentException("Struct has no property $name")
        }
        ctx.add(Push(elementIndex))
        ctx.add(StructElement())
        return type.elements[name] ?: throw IllegalStateException()
    }
}
