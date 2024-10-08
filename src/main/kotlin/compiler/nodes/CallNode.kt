package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Call
import vm.operations.Ext
import vm.operations.Free
import vm.operations.Print
import vm.operations.Println

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        val fnc = func.evaluate(env) as FuncValue
        val args = args.map {
            it.evaluate(env)
        }
        return fnc.run(args = args, it = null)
    }

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
        ctx.add(Call())
        return type
    }
}
