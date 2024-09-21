package compiler.nodes

import compiler.CompilerContext
import compiler.Environment

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
        val parentType = parent.compile(ctx)
        val argsType = args.orEmpty().reversed().map { it.compile(ctx) }
        val typeProps = propMap[parentType.type] ?: throw IllegalArgumentException("Type ${parentType.type} has no $name property")
        val prop = typeProps[name] ?: throw IllegalArgumentException("Property $name of ${parentType.type} is not supported")
        return prop.compile(parentType, args = argsType, ctx)
    }
}