package nodes

import Environment

abstract class Node {
    abstract fun evaluate(env: Environment<Type<*>>): Type<*>
}

abstract class Type<T>(private val t: T) {

    private val exts = HashMap<String, LambdaType>()

    fun value(): T = t
    open fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "ext" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'ext' requires one named lambda argument")
                }
                val lambda = args[0] as? LambdaType
                    ?: throw IllegalArgumentException("Property 'ext' requires lambda argument")
                val extName = lambda.name()
                    ?: throw IllegalArgumentException("Property 'ext' requires named lambda argument")
                exts[extName] = lambda
                return this
            }

            else -> exts[name]?.value()?.invoke(args.orEmpty(), this) ?: BoolType(false)
        }

    }
}
