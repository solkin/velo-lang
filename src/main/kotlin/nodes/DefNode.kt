package nodes

import CompilerContext
import Environment
import vm2.operations.Def
import vm2.operations.Push

data class DefNode(
    val name: String,
    val type: Int,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val value = def?.let { def.evaluate(env) } ?: VoidType()
        env.def(name, value)
        return value
    }

    override fun compile(ctx: CompilerContext): Int {
        def?.compile(ctx) ?: let { ctx.add(Push(value = type.unmask().getDefault())) }
        val v = ctx.defVar(name, type)
        ctx.add(Def(v.index))
        return DataType.VOID.mask()
    }
}
