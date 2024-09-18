package nodes

import CompilerContext
import Environment
import vm2.operations.Def
import vm2.operations.Push

data class DefNode(
    val name: String,
    val type: VMType,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val value = def?.let { def.evaluate(env) } ?: VoidType()
        env.def(name, value)
        return value
    }

    override fun compile(ctx: CompilerContext): VMType {
        val defType = def?.compile(ctx) ?: let {
            type.default.forEach { ctx.add(Push(value = it)) }
            type
        }
        if (type != defType) {
            throw IllegalArgumentException("Illegal assign type ${defType.type} != ${type.type}")
        }
        val v = ctx.defVar(name, type)
        ctx.add(Def(v.index))
        return VMVoid
    }
}
