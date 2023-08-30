package nodes

import Environment
import java.util.TreeMap

data class TreeNode(
    val treeOf: List<Node>,
) : Node() {

    private val tree = TreeMap<Type<*>, Type<*>>(TypeComparator())

    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        tree.clear()
        treeOf.forEach { item ->
            val entry = item.evaluate(env)
            if (entry !is PairType) {
                throw IllegalArgumentException("Pair expected as tree constructor argument")
            }
            val key = entry.value().first
            val value = entry.value().second
            tree[key] = value
        }
        return TreeType(tree)
    }
}

class TreeType(val tree: TreeMap<Type<*>, Type<*>>) : Type<TreeMap<Type<*>, Type<*>>>(tree), Indexable {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "len" -> IntType(tree.size)
            "keys" -> {
                ListType(tree.keys.toList())
            }

            "values" -> {
                ListType(tree.values.toList())
            }

            "reduce" -> {
                if (args?.size != 1 || args[0] !is FuncType) {
                    throw IllegalArgumentException("Property 'reduce' requires one func argument")
                }
                val func = args[0] as FuncType
                var acc: Type<*> = VoidType()
                tree.forEach { entry ->
                    val pair = PairType(entry.key, entry.value)
                    acc = if (acc is VoidType) {
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
                    if (item is PairType) {
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

    override fun get(key: Type<*>): Type<*> {
        return tree[key] ?: VoidType()
    }
}
