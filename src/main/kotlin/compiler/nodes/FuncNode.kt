package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Def
import vm.operations.Ext
import vm.operations.Free
import vm.operations.Move
import vm.operations.Pc
import vm.operations.Plus
import vm.operations.Push
import vm.operations.Ret

data class FuncNode(
    val name: String?,
    val defs: List<DefNode>,
    val type: Type,
    val body: Node,
) : Node() {

    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        var e = env
        val named = !name.isNullOrEmpty()
//        if (named) e = env.extend()

        val func = FuncValue(
            fun(args: List<Value<*>>, it: Value<*>?): Value<*> {
                val scope = e.extend()
                it?.let {
                    scope.def("it", it)
                }
                defs.forEachIndexed { i, s ->
                    scope.def(s.name, if (i < args.size) args[i] else BoolValue(false))
                }
                return body.evaluate(scope)
            },
            name
        )
        if (named) e.def(name.orEmpty(), func)

        return func
    }

    override fun compile(ctx: Context): Type {
        var resultType: Type = FuncType(derived = type)

        // Insert function address to stack
        val named = !name.isNullOrEmpty()
        val defCmdCount = if (named) 5 else 4 // Five/four commands from Pc() to function body
        ctx.add(Pc())
        ctx.add(Push(value = defCmdCount))
        ctx.add(Plus())
        // Define var and move address to var if name is defined
        if (named) {
            val v = ctx.enumerator.def(name.orEmpty(), resultType)
            ctx.add(Def(v.index))
            resultType = VoidType
        }

        // Compile body
        val funcOps = ctx.fork()
        funcOps.enumerator.extend()
        funcOps.add(Ext())
        defs.reversed().forEach { def ->
            val v = funcOps.enumerator.def(def.name, def.type)
            funcOps.add(Def(v.index))
        }
        val retType = body.compile(funcOps)
        if (retType != type) {
            throw IllegalStateException("Function $name return type $retType is not the same as defined $type")
        }
        funcOps.enumerator.free()
        funcOps.add(Free())
        funcOps.add(Ret())

        // Skip function body
        ctx.add(Move(funcOps.size()))

        // Add function operations to real context
        ctx.merge(funcOps)

        return resultType
    }
}

data class FuncType(val derived: Type) : Type {
    override val type: BaseType
        get() = BaseType.FUNCTION

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }
}

class FuncValue(val value: (args: List<Value<*>>, it: Value<*>?) -> Value<*>, val name: String? = null) :
    Value<(List<Value<*>>, Value<*>?) -> Value<*>>(value) {

    fun name() = name

    fun run(args: List<Value<*>>, it: Value<*>? = null): Value<*> {
        return value.invoke(args, it)
    }

}
