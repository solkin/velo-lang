package nodes

import Environment

abstract class Node {
    abstract fun evaluate(env: Environment<Type<*>>): Type<*>
}

abstract class Type<T>(private val t: T) {

    private val exts = HashMap<String, FuncType>()

    fun value(): T = t

    open fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "ext" -> {
                if (args?.size != 1) {
                    throw IllegalArgumentException("Property 'ext' requires one named func argument")
                }
                val func = args[0] as? FuncType
                    ?: throw IllegalArgumentException("Property 'ext' requires func argument")
                val extName = func.name()
                    ?: throw IllegalArgumentException("Property 'ext' requires named func argument")
                exts[extName] = func
                return this
            }

            else -> exts[name]?.run(args = args.orEmpty(), it = this) ?: BoolType(false)
        }
    }

}
