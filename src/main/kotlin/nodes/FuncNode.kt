package nodes

import CompilerContext
import Environment
import vm2.operations.Abs
import vm2.operations.Def
import vm2.operations.Minus
import vm2.operations.Move
import vm2.operations.Pc
import vm2.operations.Push
import vm2.operations.Ret

data class FuncNode(
    val name: String?,
    val defs: List<DefNode>,
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

    override fun compile(ctx: CompilerContext): DataType {
        val funcOps = ctx.fork()
        defs.reversed().forEach { def ->
            val v = ctx.defVar(def.name, def.type)
            funcOps.add(Def(v.index))
        }
        val type = body.compile(funcOps)
        funcOps.add(Ret())

        ctx.add(Move(funcOps.size()))

        ctx.merge(funcOps)

        ctx.add(Pc())
        ctx.add(Push(funcOps.size()))
        ctx.add(Minus())
        ctx.add(Abs())

        if (!name.isNullOrEmpty()) {
            val v = ctx.defVar(name, DataType.FUNCTION)
            ctx.add(Def(v.index))
        }
        return type
    }
}

class FuncType(val value: (args: List<Type<*>>, it: Type<*>?) -> Type<*>, val name: String? = null) :
    Type<(List<Type<*>>, Type<*>?) -> Type<*>>(value) {

    fun name() = name

    fun run(args: List<Type<*>>, it: Type<*>? = null): Type<*> {
        return value.invoke(args, it)
    }

}
