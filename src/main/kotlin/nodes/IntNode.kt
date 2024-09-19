package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class IntNode(
    val value: Int,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = IntValue(value)

    override fun compile(ctx: CompilerContext): Type {
        ctx.add(Push(value))
        return IntType
    }
}

class IntValue(val value: Int) : Value<Int>(value)
