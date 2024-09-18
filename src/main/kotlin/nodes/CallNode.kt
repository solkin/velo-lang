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

    override fun compile(ctx: CompilerContext): Int {
        args.forEach { arg ->
            arg.compile(ctx)
        }
        if (func is VarNode && func.name == "println") {
            ctx.add(Println())
            return DataType.VOID.mask()
        }
        if (func is VarNode && func.name == "print") {
            ctx.add(Print())
            return DataType.VOID.mask()
        }
        val type = func.compile(ctx)
            .takeIf { it.unmask() == DataType.FUNCTION }
            ?.unmask(depth = 2)
            ?.mask()
            ?: throw IllegalArgumentException("Call on non-function type")
        ctx.add(Call())
        return type
    }
}