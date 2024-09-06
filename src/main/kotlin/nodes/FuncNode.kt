package nodes

import Environment
import vm2.Operation
import vm2.operations.*
import vm2.operations.Set

data class FuncNode(
    val name: String?,
    val vars: List<String>,
    val body: Node,
) : Node() {

    private var addr = 0

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
        if (addr == 0) {
            val funcOps: MutableList<Operation> = ArrayList()
            vars.forEach { v ->
                val index = v.hashCode()
                funcOps.add(Def(index))
                funcOps.add(Set(index))
            }
            body.compile(funcOps)
            funcOps.add(Ret()) // TODO: create ReturnNode

            ops.add(Skip(funcOps.size))

            addr = ops.size
            ops.addAll(funcOps)
        }

        if (!name.isNullOrEmpty()) {
            ops.add(Push(addr))
            ops.add(Def(name.hashCode()))
        }

        ops.add(Push(addr))
    }
}

class FuncType(val value: (args: List<Type<*>>, it: Type<*>?) -> Type<*>, val name: String? = null) :
    Type<(List<Type<*>>, Type<*>?) -> Type<*>>(value) {

    fun name() = name

    fun run(args: List<Type<*>>, it: Type<*>? = null): Type<*> {
        return value.invoke(args, it)
    }

}
