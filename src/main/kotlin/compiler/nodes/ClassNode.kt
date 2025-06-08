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

        val args = ArrayList<Type>()
        val classType: Type = ClassType(name, num = classOps.frame.num, parent = classOps, args)

        // Define class type as a variable
        val nameVar = ctx.def(name, classType)

        // Insert class frame pointer into stack
        ctx.add(Frame(num = classOps.frame.num))
        ctx.add(Set(index = nameVar.index))

        // Compile class body
        args += defs.reversed().map { def ->
            val v = classOps.def(def.name, def.type)
            classOps.add(Set(v.index))
            def.type
        }.reversed()
        body.compile(ctx = classOps)
        classOps.add(Instance())
        classOps.add(Ret())

        // Add class operations to the real context
        ctx.merge(classOps)

        return VoidType
    }
}

data class ClassType(val name: String, val num: Int? = null, val parent: Context? = null, val args: List<Type>? = null) : Type {
    override fun sameAs(type: Type): Boolean {
        return type is ClassType && type.name == name
    }

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop? {
        return ClassElementProp(name)
    }

    override fun log() = toString()
}

data class ClassElementProp(val name: String): Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        type as? ClassType ?: throw IllegalArgumentException("Class operation on non-class type $type")
        val type = ctx.get(type.name).type as? ClassType
            ?: throw IllegalArgumentException("Class $type is not defined in this scope")
        type.parent ?: throw IllegalStateException("Class prop parent context is not defined")
        val v = type.parent.frame.vars[name] ?: throw IllegalArgumentException("Class has no property $name")
        val propCtx = ctx.discrete(parent = type.parent)
        propCtx.add(Get(v.index))
        var resultType = v.type
        if (v.type is FuncType) {
            propCtx.add(Call(args = -args.size))
            resultType = v.type.derived
        }
        propCtx.add(Ret())
        ctx.merge(propCtx)
        ctx.add(Frame(num = propCtx.frame.num))
        ctx.add(Call(args = args.size, classParent = true))
        return resultType
    }
}
