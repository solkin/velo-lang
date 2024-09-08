package nodes

import Environment
import vm2.Operation
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

    override fun compile(ops: MutableList<Operation>) {
        args.forEach { arg ->
            arg.compile(ops)
        }
        if (func is VarNode && func.name == "println") {
            ops.add(Println())
            return
        }
        if (func is VarNode && func.name == "print") {
            ops.add(Print())
            return
        }
        func.compile(ops)
        ops.add(Call())
    }
}