package nodes

import Environment
import vm2.Operation
import vm2.operations.*

data class VarNode(
    val name: String,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>) = env.get(name)

    override fun compile(ops: MutableList<Operation>) {
        ops.add(Get(name.hashCode()))
    }
}
