package nodes

import Environment

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val scope = env.extend()
        var v: Type<*> = BoolType(false)
        prog.forEach { v = it.evaluate(scope) }
        return v
    }
}