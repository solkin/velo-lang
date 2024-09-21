package compiler.nodes

import compiler.CompilerContext
import compiler.Environment

class VoidNode : Node() {
    override fun evaluate(env: Environment<Value<*>>) = VoidValue()

    override fun compile(ctx: CompilerContext): Type {
        return VoidType
    }
}

object VoidType : Type {
    override val type: BaseType
        get() = BaseType.VOID

    override fun default(ctx: CompilerContext) {}
}

class VoidValue : Value<String>("")
