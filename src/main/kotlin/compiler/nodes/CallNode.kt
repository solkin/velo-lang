package compiler.nodes

import compiler.Context
import vm.operations.Call
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
        val type = (func.compile(ctx) as? FuncType)?.derived
            ?: throw IllegalArgumentException("Call on non-function type")
        ctx.add(Call(args.size))
        return type
    }
}
