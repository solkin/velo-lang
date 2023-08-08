package nodes

import Environment

data class LambdaNode(
    val name: String?,
    val vars: List<String>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        var e = env
        val named = !name.isNullOrEmpty()
        if (named) e = env.extend()

        val lambda = LambdaType(
            fun(args: List<Type<*>>): Type<*> {
                val scope = e.extend()
                vars.forEachIndexed { i, s ->
                    scope.def(s, if (i < args.size) args[i] else BoolType(false))
                }
                return body.evaluate(scope)
            }
        )
        if (named) e.def(name.orEmpty(), lambda)

        return lambda
    }
}

class LambdaType(val value: (List<Type<*>>) -> Type<*>) : Type<(List<Type<*>>) -> Type<*>>(value)
