package nodes

import Environment

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = StrType(value)
}

class StrType(val value: String) : Type<String>(value)
