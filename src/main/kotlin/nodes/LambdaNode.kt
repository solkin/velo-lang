package nodes

import Environment

data class LambdaNode(
    val name: String?,
    val vars: List<String>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        var e = env
        val named = !name.isNullOrEmpty()
        if (named) e = env.extend()

        val lambda = fun(args: List<Node>): Any {
            val scope = e.extend()
            vars.forEachIndexed { i, s ->
                scope.def(s, if (i < args.size) args[i] else false)
            }
            return body.evaluate(scope)
        }
        if (named) e.def(name.orEmpty(), lambda)

        return lambda
    }
}