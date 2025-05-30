package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.Frame
import vm.operations.Get
import vm.operations.Instance
import vm.operations.Ret
import vm.operations.Set

data class ClassNode(
    val name: String,
    val defs: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        // Create class body frame with discrete context
        val classOps = ctx.discrete()

        val classType: Type = ClassType(name, num = classOps.frame.num, parent = classOps)

        // Define class type as a variable
        val nameVar = ctx.def(name, classType)

        // Insert class frame pointer into stack
        ctx.add(Frame(num = classOps.frame.num))
        ctx.add(Set(index = nameVar.index))

        // Compile class body
        defs.reversed().forEach { def ->
            val v = classOps.def(def.name, def.type)
            classOps.add(Set(v.index))
        }
        body.compile(ctx = classOps)
        classOps.add(Instance())
        classOps.add(Ret())

        // Add class operations to the real context
        ctx.merge(classOps)

        return VoidType
    }
}

data class ClassType(val name: String, val num: Int, val parent: Context?) : Type {
    override val type: BaseType
        get() = BaseType.CLASS

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop? {
        parent ?: throw IllegalStateException("Class prop parent context is not defined")
        if (parent.frame.vars.containsKey(name)) {
            return ClassElementProp(name)
        }
        return null
    }
}

data class ClassElementProp(val name: String): Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? ClassType ?: throw IllegalArgumentException("Class operation on non-class type $type")
        type.parent ?: throw IllegalStateException("Class prop parent context is not defined")
        val v = type.parent.frame.vars[name] ?: throw IllegalArgumentException("Class has no property $name")
        val propCtx = ctx.discrete(parent = type.parent)
        propCtx.add(Get(v.index))
        if (v.type.type == BaseType.FUNCTION) {
            propCtx.add(Call(args = -args.size))
        }
        propCtx.add(Ret())
        ctx.merge(propCtx)
        ctx.add(Frame(num = propCtx.frame.num))
        ctx.add(Call(args = args.size, classParent = true))
        return type.parent.frame.vars[name]?.type ?: throw IllegalStateException()
    }
}
