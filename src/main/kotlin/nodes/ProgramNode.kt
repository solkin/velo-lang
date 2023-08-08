package nodes

import Environment

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        var v: Type<*> = BoolType(false)
        prog.forEach { v = it.evaluate(env) }
        return v
    }
}