package nodes

import Environment

data class VardefNode(
    val name: String,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val value = def?.let { def.evaluate(env) } ?: VoidType()
        env.def(name, value)
        return value
    }
}
