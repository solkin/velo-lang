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
    FUNCTION("fn"),
    VOID("void"),
    AUTO("auto"),
}

interface Type {
    val type: BaseType
    fun default(ctx: Context)
}

object ByteType : Type {
    override val type: BaseType
        get() = BaseType.BYTE

    override fun default(ctx: Context) {
        ctx.add(Push(value = 0))
    }
}

fun BaseType.getDefaultNode(): Node {
    return when (this) {
        BaseType.BYTE -> IntNode(0)
        BaseType.INT -> IntNode(0)
        BaseType.FLOAT -> FloatNode(0.0)
        BaseType.STRING -> StringNode("")
        BaseType.BOOLEAN -> BoolNode(false)
        BaseType.PAIR -> PairNode(first = VoidNode(), second = null)
        BaseType.ARRAY -> ArrayNode(listOf = emptyList(), VoidType)
        BaseType.FUNCTION -> IntNode(0)
        BaseType.VOID -> ProgramNode(emptyList())
        BaseType.AUTO -> throw Exception("Type auto has no default value")
    }
}

object AutoType : Type {
    override val type: BaseType
        get() = BaseType.AUTO

    override fun default(ctx: Context) {
        throw Exception("Type auto has no default value")
    }
}
