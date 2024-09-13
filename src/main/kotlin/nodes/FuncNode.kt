package nodes

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

    override fun compile(ops: MutableList<Operation>) {
        val funcOps: MutableList<Operation> = ArrayList()
        vars.reversed().forEach { v ->
            val index = v.hashCode()
            funcOps.add(Def(index))
        }
        body.compile(funcOps)
        funcOps.add(Ret()) // TODO: create ReturnNode

        ops.add(Move(funcOps.size))

        ops.addAll(funcOps)

        ops.add(Pc())
        ops.add(Push(funcOps.size))
        ops.add(Minus())
        ops.add(Abs())

        if (!name.isNullOrEmpty()) {
            ops.add(Def(name.hashCode()))
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
