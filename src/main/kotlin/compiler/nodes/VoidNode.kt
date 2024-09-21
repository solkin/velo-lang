package compiler.nodes

import compiler.Context
import compiler.Environment

class VoidNode : Node() {
    override fun evaluate(env: Environment<Value<*>>) = VoidValue()

    override fun compile(ctx: Context): Type {
        return VoidType
    }
}

object VoidType : Type {
    override val type: BaseType
        get() = BaseType.VOID

    override fun default(ctx: Context) {}
}

class VoidValue : Value<String>("")
