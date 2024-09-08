package nodes

import Environment
import vm2.Operation
import vm2.operations.If
import vm2.operations.Skip

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val cond = condNode.evaluate(env)
        if (cond.value() != false) return thenNode.evaluate(env)
        return elseNode?.let { elseNode.evaluate(env) } ?: BoolType(false)
    }

    override fun compile(ops: MutableList<Operation>) {
        val thenOps: MutableList<Operation> = ArrayList()
        thenNode.compile(thenOps)

        val elseOps: MutableList<Operation> = ArrayList()
        elseNode?.run {
            compile(elseOps)
            thenOps.add(Skip(elseOps.size))
        }

        condNode.compile(ops)
        val elseSkip = thenOps.size
        ops.add(If(elseSkip))
        ops.addAll(thenOps)
        if (elseOps.isNotEmpty()) {
            ops.addAll(elseOps)
        }
    }
}