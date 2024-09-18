package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = StrType(value)

    override fun compile(ctx: CompilerContext): Int {
        ctx.add(Push(value))
        return DataType.STRING.mask()
    }
}

class StrType(val value: String) : Type<String>(value) {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "len" -> IntType(value.length)
            "hash" -> IntType(value.hashCode())
            "str" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0].toInt()
                val end = args[1].toInt()
                StrType(value.substring(start, end))
            }

            else -> super.property(name, args)
        }
    }
}
