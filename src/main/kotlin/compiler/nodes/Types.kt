package compiler.nodes

import compiler.Context

interface Type {
    fun sameAs(type: Type): Boolean
    fun default(ctx: Context)
    fun prop(name: String): Prop?
    fun log(): String
    fun vmType(): Byte
}

interface Callable : Type {
    val args: List<Type>?
}

interface Numeric : Type

object AutoType : Type {
    override fun sameAs(type: Type) = true

    override fun default(ctx: Context) {
        throw Exception("Type auto has no default value")
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()

    override fun vmType() = vm.AUTO
}
