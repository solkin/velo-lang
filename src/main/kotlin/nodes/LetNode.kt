package nodes

import Environment

data class LetNode(
    val vars: List<VardefNode>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any {
        var e = env
        vars.forEach { v ->
            val scope = e.extend()
            scope.def(v.name, v.def?.let { v.def.evaluate(e) } ?: FALSE)
            e = scope
        }
        return body.evaluate(e)
    }
}