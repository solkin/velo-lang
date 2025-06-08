package compiler.nodes

import compiler.Context
import vm.operations.Set
import vm.operations.Frame
import vm.operations.Ret

data class FuncNode(
    val name: String?,
    val defs: List<DefNode>,
    val type: Type,
    val body: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val args = ArrayList<Type>()
        var resultType: Type = FuncType(derived = type, args)

        // Define var before body frame creation (because var counter will be forked) if name is defined
        val named = !name.isNullOrEmpty()
        val nameVar = if (named) ctx.def(name.orEmpty(), resultType) else null

        // Create body frame and fork var counter
        val funcOps = ctx.extend()

        // Insert function frame pointer into stack
        ctx.add(Frame(num = funcOps.frame.num))
        // Define var if named variable defined
        nameVar?.let {
            ctx.add(Set(index = nameVar.index))
            resultType = VoidType
        }

        // Compile body
        args += defs.reversed().map { def ->
            val v = funcOps.def(def.name, def.type)
            funcOps.add(Set(v.index))
            def.type
        }.reversed()
        val retType = body.compile(funcOps)
        if (retType != type) {
            throw IllegalStateException("Function $name return type $retType is not the same as defined $type")
        }
        funcOps.add(Ret())

        // Add function operations to the real context
        ctx.merge(funcOps)

        return resultType
    }
}

data class FuncType(val derived: Type, override val args: List<Type>? = null) : Callable {
    override fun sameAs(type: Type): Boolean {
        return type is FuncType && type.derived.sameAs(derived)
    }

    override fun default(ctx: Context) {
        ctx.add(Frame(num = 0))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()
}
