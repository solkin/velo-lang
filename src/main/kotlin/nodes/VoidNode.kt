package nodes

import CompilerContext
import Environment

class VoidNode : Node() {
    override fun evaluate(env: Environment<Value<*>>) = VoidValue()

    override fun compile(ctx: CompilerContext): Type {
        return VoidType
    }
}

class VoidValue : Value<String>("")
