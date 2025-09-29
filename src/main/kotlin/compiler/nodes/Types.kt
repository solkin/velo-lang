package compiler.nodes

import compiler.Context
import vm.VmType

interface Type {
    fun sameAs(type: Type): Boolean
    fun default(ctx: Context)
    fun prop(name: String): Prop?
    fun log(): String
    fun vmType(): VmType
    fun name(): String
}

interface Callable : Type {
    val args: List<Type>?
}

interface Numeric : Type

interface Indexable: Type {
    fun compileIndex(ctx: Context): Type
}

interface IndexAssignable: Indexable {
    fun compileAssignment(ctx: Context)
}

object AnyType : Type {
    override fun sameAs(type: Type) = true

    override fun default(ctx: Context) {
        throw Exception("Type 'any' has no default value")
    }

    override fun prop(name: String): Prop? = null

    override fun log() = toString()

    override fun vmType() = vm.VmAny()

    override fun name() = "any"
}
