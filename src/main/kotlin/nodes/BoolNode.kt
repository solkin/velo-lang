package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class BoolNode(
    val value: Boolean,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = BoolType(value)

    override fun compile(ctx: CompilerContext): Int {
        ctx.add(Push(value))
        return DataType.BOOLEAN.mask()
    }
}

class BoolType(val value: Boolean) : Type<Boolean>(value)

val FALSE = BoolNode(value = false)