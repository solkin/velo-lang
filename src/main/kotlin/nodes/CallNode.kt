package nodes

import CompilerContext
import Environment
import vm2.operations.Call
import vm2.operations.Print
import vm2.operations.Println

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val fnc = func.evaluate(env) as FuncType
        val args = args.map {
            it.evaluate(env)
        }
        return fnc.run(args = args, it = null)
    }

    override fun compile(ctx: CompilerContext): DataType {
        args.forEach { arg ->
            arg.compile(ctx)
        }
        if (func is VarNode && func.name == "println") {
            ctx.add(Println())
            return DataType.VOID
        }
        if (func is VarNode && func.name == "print") {
            ctx.add(Print())
            return DataType.VOID
        }
        val type = func.compile(ctx)
        ctx.add(Call())
        return type
    }
}