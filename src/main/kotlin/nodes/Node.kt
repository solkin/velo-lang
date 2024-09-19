package nodes

import CompilerContext
import Environment

abstract class Node {
    abstract fun evaluate(env: Environment<Value<*>>): Value<*>
    open fun compile(ctx: CompilerContext): Type {
        throw NotImplementedError("Compile function for $this is not implemented")
    }
}

abstract class Value<T>(private val t: T) {

    private val exts = HashMap<String, FuncValue>()

    fun value(): T = t

    open fun property(name: String, args: List<Value<*>>?): Value<*> {
        return exts[name]?.run(args = args.orEmpty(), it = this) ?: when (name) {
            "string" -> StringValue(toString())
            "ext" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'ext' requires one named func argument")
                }
                val func = args[0] as? FuncValue
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
                PairValue(this, args[0])
            }

            else -> VoidValue()
        }
    }

    override fun toString(): String {
        return value().toString()
    }
}

interface Indexable {
    fun get(key: Value<*>): Value<*>
}

class ValueComparator : Comparator<Value<*>> {
    override fun compare(o1: Value<*>, o2: Value<*>): Int {
        return o1.compareTo(o2)
    }
}
