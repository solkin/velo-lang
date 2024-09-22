package compiler.nodes

import compiler.Environment
import java.util.TreeMap

data class TreeNode(
    val treeOf: List<Node>,
) : Node() {

    private val tree = TreeMap<Value<*>, Value<*>>(ValueComparator())

    override fun evaluate(env: Environment<Value<*>>): Value<*> {
        tree.clear()
        treeOf.forEach { item ->
            val entry = item.evaluate(env)
            if (entry !is PairValue) {
                throw IllegalArgumentException("Pair expected as tree constructor argument")
            }
            val key = entry.value().first
            val value = entry.value().second
            tree[key] = value
        }
        return TreeValue(tree)
    }
}

class TreeValue(val tree: TreeMap<Value<*>, Value<*>>) : Value<TreeMap<Value<*>, Value<*>>>(tree), Indexable {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "len" -> IntValue(tree.size)
            "keys" -> {
                ArrayValue(tree.keys.toList())
            }

            "values" -> {
                ArrayValue(tree.values.toList())
            }

            "reduce" -> {
                if (args?.size != 1 || args[0] !is FuncValue) {
                    throw IllegalArgumentException("Property 'reduce' requires one func argument")
                }
                val func = args[0] as FuncValue
                var acc: Value<*> = VoidValue()
                tree.forEach { entry ->
                    val pair = PairValue(entry.key, entry.value)
                    acc = if (acc is VoidValue) {
                        pair
                    } else {
                        func.run(args = listOf(acc, pair), it = this)
                    }
                }
                acc
            }

            "put" -> {
                if (args == null) {
                    throw IllegalArgumentException("Property 'put' requires at least one pair argument")
                }
                args.forEach { item ->
                    if (item is PairValue) {
                        tree[item.value().first] = item.value().second
                    } else {
                        throw IllegalArgumentException("Property 'put' requires pairs as argument")
                    }
                }
                return this
            }

            else -> super.property(name, args)
        }
    }

    override fun get(key: Value<*>): Value<*> {
        return tree[key] ?: VoidValue()
    }
}
