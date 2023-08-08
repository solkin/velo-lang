package nodes

import Environment

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val fnc = func.evaluate(env) as LambdaType
        val args = args.map {
            it.evaluate(env)
        }
        return fnc.run(args = args, it = null)
    }
}