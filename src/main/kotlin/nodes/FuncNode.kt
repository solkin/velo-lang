package nodes

import CompilerContext
import Environment
import vm2.Operation
import vm2.operations.*

data class FuncNode(
    val name: String?,
    val vars: List<String>,
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
                vars.forEachIndexed { i, s ->
                    scope.def(s, if (i < args.size) args[i] else BoolType(false))
                }
                return body.evaluate(scope)
            },
            name
        )
        if (named) e.def(name.orEmpty(), func)

        return func
    }

    override fun compile(ctx: CompilerContext) {
        val funcOps = ctx.fork()
        vars.reversed().forEach { v ->
            val index = ctx.varIndex(v)
            funcOps.add(Def(index))
        }
        body.compile(funcOps)
        funcOps.add(Ret())

        ctx.add(Move(funcOps.size()))

        ctx.merge(funcOps)

        ctx.add(Pc())
        ctx.add(Push(funcOps.size()))
        ctx.add(Minus())
        ctx.add(Abs())

        if (!name.isNullOrEmpty()) {
            ctx.add(Def(ctx.varIndex(name)))
        }
    }
}

class FuncType(val value: (args: List<Type<*>>, it: Type<*>?) -> Type<*>, val name: String? = null) :
    Type<(List<Type<*>>, Type<*>?) -> Type<*>>(value) {

    fun name() = name

    fun run(args: List<Type<*>>, it: Type<*>? = null): Type<*> {
        return value.invoke(args, it)
    }

}
