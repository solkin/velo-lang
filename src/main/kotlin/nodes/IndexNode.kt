package nodes

import CompilerContext
import Environment
import vm2.operations.Index

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val l = list.evaluate(env) as? Indexable
            ?: throw IllegalArgumentException("Access index of non-indexable type")
        val i = index.evaluate(env)
        return l.get(i)
    }

    override fun compile(ctx: CompilerContext): DataType {
        list.compile(ctx)
        index.compile(ctx)
        ctx.add(Index())
        return DataType.VOID // TODO: replace with real type
    }
}