package nodes

import Environment

data class LambdaNode(
    val vars: List<String>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any = fun(args: List<Node>): Any {
        val scope = env.extend()
        vars.forEachIndexed { i, s ->
            scope.def(s, if (i < args.size) args[i] else false)
        }
        return body.evaluate(scope)
    }
}