package nodes

import Environment
import vm2.Operation

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val v = parent.evaluate(env)
        val a = args?.map { it.evaluate(env) }
        return v.property(name, a)
    }

    override fun compile(ops: MutableList<Operation>) {
        parent.compile(ops)
        args.orEmpty().reversed().forEach { it.compile(ops) }
        parent.property(name, ops)
    }
}