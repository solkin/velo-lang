package nodes

import Environment
import vm2.Operation
import vm2.operations.Ext
import vm2.operations.Free

data class LetNode(
    val vars: List<DefNode>,
    val body: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val scope = env.extend()
        vars.forEach { v ->
            v.evaluate(scope)
        }
        return body.evaluate(scope)
    }

    override fun compile(ops: MutableList<Operation>) {
        ops.add(Ext())
        vars.forEach { it.compile(ops) }
        body.compile(ops)
        ops.add(Free())
    }
}