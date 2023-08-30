package nodes

import Environment

abstract class Node {
    abstract fun evaluate(env: Environment<Type<*>>): Type<*>
}

abstract class Type<T>(private val t: T) {

    private val exts = HashMap<String, FuncType>()

    fun value(): T = t

    open fun property(name: String, args: List<Type<*>>?): Type<*> {
        return exts[name]?.run(args = args.orEmpty(), it = this) ?: when (name) {
            "string" -> StrType(toString())
            "ext" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'ext' requires one named func argument")
                }
                val func = args[0] as? FuncType
                    ?: throw IllegalArgumentException("Property 'ext' requires func argument")
                val extName = func.name()
                    ?: throw IllegalArgumentException("Property 'ext' requires named func argument")
                exts[extName] = func
                this
            }

            "to" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'to' requires one argument")
                }
                PairType(this, args[0])
            }

            else -> VoidType()
        }
    }

    override fun toString(): String {
        return value().toString()
    }
}

interface Indexable {
    fun get(key: Type<*>): Type<*>
}

class TypeComparator : Comparator<Type<*>> {
    override fun compare(o1: Type<*>, o2: Type<*>): Int {
        return o1.compareTo(o2)
    }
}
