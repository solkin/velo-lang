package nodes

import CompilerContext
import Environment
import vm2.operations.Set

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        return env.set(left.name, right.evaluate(env))
    }

    override fun compile(ctx: CompilerContext): VMType {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        val type = right.compile(ctx)
        val v = ctx.getVar(left.name) ?: throw IllegalArgumentException("Variable ${left.name} is not defined")
        if (v.type.type != type.type) {
            throw IllegalArgumentException("Illegal assign type ${type.type} != ${v.type.type}")
        }
        ctx.add(Set(v.index))
        return VMVoid
    }
}