package nodes

import CompilerContext
import Environment
import vm2.operations.Push

data class DoubleNode(
    val value: Double,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = DoubleType(value)

    override fun compile(ctx: CompilerContext): Int {
        ctx.add(Push(value))
        return DataType.FLOAT.mask()
    }
}

class DoubleType(val value: Double) : Type<Double>(value)
