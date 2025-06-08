package compiler.nodes

import compiler.Context
import vm.operations.Push

interface Type {
    fun sameAs(type: Type): Boolean
    fun default(ctx: Context)
    fun prop(name: String): Prop?
    fun log(): String
}

interface Callable : Type {
    val args: List<Type>?
}

object ByteType : Type {
    override fun sameAs(type: Type): Boolean {
        return type is ByteType
    }

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()
}

object AutoType : Type {
    override fun sameAs(type: Type) = true

    override fun default(ctx: Context) {
        throw Exception("Type auto has no default value")
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()
}
