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
        args.forEach { arg ->
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
        val type = when (returnType) {
            is FuncType -> returnType.derived
            is ClassType -> returnType // TODO: named type
            else -> throw IllegalArgumentException("Call on non-function type")
        }
        ctx.add(Call(args.size))
        return type
    }
}
