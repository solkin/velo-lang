package nodes

import CompilerContext
import Environment

class VoidNode : Node() {
    override fun evaluate(env: Environment<Type<*>>) = VoidType()

    override fun compile(ctx: CompilerContext): VMType {
        return VMVoid
    }
}

class VoidType : Type<String>("")
