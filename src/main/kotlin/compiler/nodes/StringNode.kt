package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Push
import vm.operations.StrLen
import vm.operations.SubStr

data class StringNode(
    val value: String,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = StringValue(value)

    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return StringType
    }
}

object StringType : Type {
    override val type: BaseType
        get() = BaseType.STRING

    override fun default(ctx: Context) {
        ctx.add(Push(value = ""))
    }
}

object SubStrProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(SubStr())
        return StringType
    }
}

object StrLenProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(StrLen())
        return IntType
    }
}

class StringValue(val value: String) : Value<String>(value) {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "len" -> IntValue(value.length)
            "hash" -> IntValue(value.hashCode())
            "sub" -> {
                if (args?.size != 2) {
                    throw IllegalArgumentException("Property 'sub' requires (start, end) arguments")
                }
                val start = args[0].toInt()
                val end = args[1].toInt()
                StringValue(value.substring(start, end))
            }

            else -> super.property(name, args)
        }
    }
}
