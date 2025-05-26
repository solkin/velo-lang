package compiler.nodes

import compiler.Context
import vm.operations.Set
import vm.operations.Frame
import vm.operations.MakeStruct
import vm.operations.Push
import vm.operations.Ret
import vm.operations.StructElement

data class StructNode(
    val name: String,
    val defs: List<DefNode>,
) : Node() {
    override fun compile(ctx: Context): Type {
        val elements = LinkedHashMap<String, Type>()
        defs.reversed().forEach { def ->
            elements[def.name] = def.type
        }

        val resultType: Type = FuncType(derived = StructType(elements))

        // Compile body
        val funcOps = ctx.extend()
        // Compile function body - structure constructor
        funcOps.add(Push(value = elements.size))
        funcOps.add(MakeStruct())
        funcOps.add(Ret())

        // Insert function address to stack
        ctx.add(Frame(num = funcOps.frame.num))
        // Define named var
        val v = ctx.def(name, resultType)
        ctx.add(Set(index = v.index))

        // Add function operations to real context
        ctx.merge(funcOps)

        return VoidType
    }
}

data class StructType(val elements: Map<String, Type>) : Type {
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
