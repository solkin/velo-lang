package compiler.nodes

import compiler.Context
import vm.operations.Frame
import vm.operations.Instance
import vm.operations.Ret
import vm.operations.Set

data class ClassNode(
    val name: String,
    val defs: List<DefNode>,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val classType: Type = ClassType(name)

        // Define class type as a variable
        val nameVar = ctx.def(name, classType)

        // Create class body frame with discrete context
        val classOps = ctx.discrete()

        // Insert class frame pointer into stack
        ctx.add(Frame(num = classOps.frame.num))
        ctx.add(Set(index = nameVar.index))

        // Compile class body
        defs.reversed().forEach { def ->
            val v = classOps.def(def.name, def.type)
            classOps.add(Set(v.index))
        }
        body.compile(classOps)
        classOps.add(Instance())
        classOps.add(Ret())

        // Add class operations to the real context
        ctx.merge(classOps)

        return VoidType
    }
}

data class ClassType(val name: String) : Type {
    override val type: BaseType
        get() = BaseType.CLASS

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop? = null
}
