package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.Input
import vm.operations.Print
import vm.operations.Println

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun compile(ctx: Context): Type {
        val argTypes = args.map { arg ->
            arg.compile(ctx)
        }
        if (func is VarNode && func.name == "println") {
            ctx.add(Println())
            return VoidType
        }
        if (func is VarNode && func.name == "print") {
            ctx.add(Print())
            return VoidType
        }
        if (func is VarNode && func.name == "input") {
            ctx.add(Input())
            return StringType
        }
        val returnType = func.compile(ctx)
        if (returnType is Callable) {
            val funcArgTypes = returnType.args ?: throw Exception("Callable type arguments is not defined")
            if (funcArgTypes.size != argTypes.size) {
                throw Exception("Call args count ${argTypes.size} is differ from required ${funcArgTypes.size}")
            }
            funcArgTypes.forEachIndexed { i, def ->
                val argType = argTypes[i]
                if (!argType.sameAs(def)) {
                    throw Exception("Argument \"${argType.log()}\" is differ from required type ${def.log()}")
                }
            }
        }
        val type = when (returnType) {
            is FuncType -> returnType.derived
            is ClassType -> returnType
            else -> throw IllegalArgumentException("Call on non-function type")
        }
        ctx.add(Call(args.size))
        return type
    }
}
