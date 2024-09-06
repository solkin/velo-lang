package nodes

import Environment
import vm2.Operation

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val scope = env.extend()
        var v: Type<*> = BoolType(false)
        prog.forEach { v = it.evaluate(scope) }
        return v
    }

    override fun compile(ops: MutableList<Operation>) {
        prog.forEach { it.compile(ops) }
    }
}