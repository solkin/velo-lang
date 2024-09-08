package nodes

import Environment
import vm2.Operation
import vm2.operations.If
import vm2.operations.Move

data class WhileNode(
    val cond: Node,
    val expr: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        while (cond.evaluate(env).value() != false) {
            expr.evaluate(env)
        }
        return BoolType(false)
    }

    override fun compile(ops: MutableList<Operation>) {
        val condOps: MutableList<Operation> = ArrayList()
        cond.compile(condOps)

        val exprOps: MutableList<Operation> = ArrayList()
        expr.compile(exprOps)
        exprOps.add(Move(-(exprOps.size + condOps.size + 2))) // +2 because to move and if is not included

        ops.addAll(condOps)
        ops.add(If(exprOps.size))
        ops.addAll(exprOps)
    }
}
