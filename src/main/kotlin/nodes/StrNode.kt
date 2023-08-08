package nodes

import Environment

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = StrType(value)
}

class StrType(val value: String) : Type<String>(value) {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "len" -> NumType(value.length.toDouble())
            "hash" -> NumType(value.hashCode().toDouble())
            "sub" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0] as NumType
                val end = args[1] as NumType
                StrType(value.substring(start.value.toInt(), end.value.toInt()))
            }

            else -> super.property(name, args)
        }
    }
}
