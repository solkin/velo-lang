package compiler.nodes

import CompilerContext
import compiler.Environment
import vm2.operations.Def

data class DefNode(
    val name: String,
    val type: Type,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        val value = def?.let { def.evaluate(env) } ?: VoidValue()
        env.def(name, value)
        return value
    }

    override fun compile(ctx: CompilerContext): Type {
        val defType = def?.compile(ctx) ?: let {
            type.default(ctx)
            type
        }
        if (type != defType) {
            throw IllegalArgumentException("Illegal assign type $defType != $type")
        }
        val v = ctx.defVar(name, type)
        ctx.add(Def(v.index))
        return VoidType
    }
}
