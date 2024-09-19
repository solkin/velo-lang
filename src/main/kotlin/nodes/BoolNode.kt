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
        return BooleanType
    }
}

class BoolValue(val value: Boolean) : Value<Boolean>(value)
