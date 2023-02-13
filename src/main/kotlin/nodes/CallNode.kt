package nodes

import Environment

data class CallNode(
    val func: Node,
    val args: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        val func = func.evaluate(env) as ((Any) -> Any)
        val args = args.map {
            it.evaluate(env)
        }
        return func.invoke(args)
    }
}