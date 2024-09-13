package nodes

import Environment
import vm2.Operation
import vm2.operations.Push
import vm2.operations.StrLen
import vm2.operations.SubStr

data class StrNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = StrType(value)

    override fun compile(ops: MutableList<Operation>) {
        ops.add(Push(value))
    }

    override fun property(name: String, ops: MutableList<Operation>) {
        when(name) {
            "str" -> ops.add(SubStr())
            "len" -> ops.add(StrLen())
            else -> throw IllegalArgumentException("Property $name is not supported")
        }
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
