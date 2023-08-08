package nodes

import Environment

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = StrType(value)
}

class StrType(val value: String) : Type<String>(value) {
    override fun property(name: String): Type<*> {
        return when (name) {
            "len" -> NumType(value.length.toDouble())
            else -> super.property(name)
        }
    }
}
