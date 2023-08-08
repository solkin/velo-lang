package nodes

import Environment

data class LetNode(
    val vars: List<VardefNode>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        var e = env
        val scope = e.extend() // TODO: check for location here or in forEach
        vars.forEach { v ->
            scope.def(v.name, v.def?.let { v.def.evaluate(e) } ?: BoolType(false))
            e = scope
        }
        return body.evaluate(e)
    }
}