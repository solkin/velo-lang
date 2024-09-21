package compiler.nodes

import compiler.CompilerContext
import compiler.Environment
import vm.operations.Push

data class IntNode(
    val value: Int,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>) = IntValue(value)

    override fun compile(ctx: CompilerContext): Type {
        ctx.add(Push(value))
        return IntType
    }
}

object IntType : Type {
    override val type: BaseType
        get() = BaseType.INT

    override fun default(ctx: CompilerContext) {
        ctx.add(Push(value = 0))
    }
}

class IntValue(val value: Int) : Value<Int>(value)
