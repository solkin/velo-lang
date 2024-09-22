package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.IntStr
import vm.operations.Push
import vm.operations.StrCon

data class IntNode(
    val value: Int,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = IntValue(value)

    override fun compile(ctx: Context): Type {
        ctx.add(Push(value))
        return IntType
    }
}

object IntType : Type {
    override val type: BaseType
        get() = BaseType.INT

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }
}

object IntStrProp: Prop {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(IntStr())
        return StringType
    }
}

class IntValue(val value: Int) : Value<Int>(value) {

    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "str" -> StringValue(value.toString())
            else -> super.property(name, args)
        }
    }

}
