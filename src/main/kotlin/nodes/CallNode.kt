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

    override fun compile(ctx: CompilerContext): VMType {
        args.forEach { arg ->
            arg.compile(ctx)
        }
        if (func is VarNode && func.name == "println") {
            ctx.add(Println())
            return VMVoid
        }
        if (func is VarNode && func.name == "print") {
            ctx.add(Print())
            return VMVoid
        }
        val type = (func.compile(ctx) as? VMFunction)?.derived
            ?: throw IllegalArgumentException("Call on non-function type")
        ctx.add(Call())
        return type
    }
}
