package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = BoolValue(value)

    override fun compile(ctx: CompilerContext): Type {
        ctx.add(Push(value))
        return BoolType
    }
}

object BoolType : Type {
    override val type: BaseType
        get() = BaseType.BOOLEAN

    override fun default(ctx: CompilerContext) {
        ctx.add(Push(value = false))
    }
}

class BoolValue(val value: Boolean) : Value<Boolean>(value)
