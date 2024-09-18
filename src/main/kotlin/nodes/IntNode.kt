package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class IntNode(
    val value: Int,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = IntType(value)

    override fun compile(ctx: CompilerContext): VMType {
        ctx.add(Push(value))
        return VMInt
    }
}

class IntType(val value: Int) : Type<Int>(value)
