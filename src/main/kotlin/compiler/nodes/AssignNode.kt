package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.Set

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        return env.set(left.name, right.evaluate(env))
    }

    override fun compile(ctx: Context): Type {
        if (left !is VarNode) throw IllegalArgumentException("Cannot assign to $left")
        val type = right.compile(ctx)
        val v = ctx.enumerator.get(left.name)
        if (v.type.type != type.type) {
            throw IllegalArgumentException("Illegal assign type $type != ${v.type}")
        }
        ctx.add(Set(v.index))
        return VoidType
    }
}