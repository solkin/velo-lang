package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Def

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

    override fun compile(ctx: Context): Type {
        val defType = def?.compile(ctx) ?: let {
            type.default(ctx)
            type
        }
        if (type != defType && type.type != AutoType.type) {
            throw IllegalArgumentException("Illegal assign type $defType != $type")
        }
        val v = ctx.enumerator.def(name, defType)
        ctx.add(Def(v.index))
        return VoidType
    }
}
