package nodes

import CompilerContext
import Environment

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        val v = parent.evaluate(env)
        val a = args?.map { it.evaluate(env) }
        return v.property(name, a)
    }

    override fun compile(ctx: CompilerContext): Type {
        val type = parent.compile(ctx)
        val argsType = args.orEmpty().reversed().map { it.compile(ctx) }
        val typeProps = propMap[type.type] ?: throw IllegalArgumentException("Type ${type.type} has no $name propery")
        val prop = typeProps[name] ?: throw IllegalArgumentException("Property $name of ${type.type} is not supported")
        return prop.compile(type, args = argsType, ctx)
    }
}