package compiler.nodes

import compiler.Context
import vm.operations.Push

enum class BaseType(val type: String) {
    BYTE("byte"),
    INT("int"),
    FLOAT("float"),
    STRING("str"),
    BOOLEAN("bool"),
    PAIR("pair"),
    ARRAY("array"),
    DICT("dict"),
    STRUCT("struct"),
    CLASS("class"),
    FUNCTION("func"),
    VOID("void"),
    AUTO("auto"),
}

interface Type {
    fun sameAs(type: Type): Boolean
    fun default(ctx: Context)
    fun prop(name: String): Prop?
    fun log(): String
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
