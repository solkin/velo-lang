package nodes

import CompilerContext
import Environment
import vm2.operations.Abs
import vm2.operations.Def
import vm2.operations.Minus
import vm2.operations.Move
import vm2.operations.Pc
import vm2.operations.Plus
import vm2.operations.Push
import vm2.operations.Ret

data class FuncNode(
    val name: String?,
    val defs: List<DefNode>,
    val type: Int,
    val body: Node,
) : Node() {

    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        var e = env
        val named = !name.isNullOrEmpty()
//        if (named) e = env.extend()

        val func = FuncType(
            fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                val scope = e.extend()
                it?.let {
                    scope.def("it", it)
                }
                defs.forEachIndexed { i, s ->
                    scope.def(s.name, if (i < args.size) args[i] else BoolType(false))
                }
                return body.evaluate(scope)
            },
            name
        )
        if (named) e.def(name.orEmpty(), func)

        return func
    }

    override fun compile(ctx: CompilerContext): Int {
        val derivedType = DataType.FUNCTION.mask().derive(depth = 2, type.unmask())

        // Insert function address to stack
        ctx.add(Pc())
        ctx.add(Push(value = 5)) // Five commands from Pc() to function body
        ctx.add(Plus())
        // Define var and move address to var if name is defined
        if (!name.isNullOrEmpty()) {
            val v = ctx.defVar(name, derivedType)
            ctx.add(Def(v.index))
        }

        // Compile body
        val funcOps = ctx.fork()
        defs.reversed().forEach { def ->
            val v = funcOps.defVar(def.name, def.type)
            funcOps.add(Def(v.index))
        }
        val retType = body.compile(funcOps)
        if (retType != type) {
            throw IllegalStateException("Function $name return type $retType is not the same as defined $type")
        }
        funcOps.add(Ret())

        // Skip function body
        ctx.add(Move(funcOps.size()))

        // Add function operations to real context
        ctx.merge(funcOps)

        return derivedType
    }
}

class FuncType(val value: (args: List<Type<*>>, it: Type<*>?) -> Type<*>, val name: String? = null) :
    Type<(List<Type<*>>, Type<*>?) -> Type<*>>(value) {

    fun name() = name

    fun run(args: List<Type<*>>, it: Type<*>? = null): Type<*> {
        return value.invoke(args, it)
    }

}
