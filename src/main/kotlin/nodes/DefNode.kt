package nodes

import Environment
import vm2.Operation
import vm2.operations.Def
import vm2.operations.Push

data class DefNode(
    val name: String,
    val def: Node?,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val value = def?.let { def.evaluate(env) } ?: VoidType()
        env.def(name, value)
        return value
    }

    override fun compile(ops: MutableList<Operation>) {
        def?.compile(ops) ?: let { ops.add(Push(value = 0)) }
        ops.add(Def(name.hashCode()))
    }
}
