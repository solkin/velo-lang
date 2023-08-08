package nodes

import Environment

abstract class Node {
    abstract fun evaluate(env: Environment<Type<*>>): Type<*>
}

abstract class Type<T>(private val t: T) {
    fun value(): T = t
    open fun property(name: String): Type<*> {
        return BoolType(false)
    }
}
