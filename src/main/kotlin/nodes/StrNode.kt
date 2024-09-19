package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = StrValue(value)

    override fun compile(ctx: CompilerContext): Type {
        ctx.add(Push(value))
        return StringType
    }
}

class StrValue(val value: String) : Value<String>(value) {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "len" -> IntValue(value.length)
            "hash" -> IntValue(value.hashCode())
            "str" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0].toInt()
                val end = args[1].toInt()
                StrValue(value.substring(start, end))
            }

            else -> super.property(name, args)
        }
    }
}
