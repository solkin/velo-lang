package nodes

import CompilerContext

enum class BaseType(val type: String) {
    BYTE("byte"),
    INT("int"),
    FLOAT("float"),
    STRING("str"),
    BOOLEAN("bool"),
    PAIR("pair"),
    SLICE("slice"),
    FUNCTION("fn"),
    VOID("void"),
}

interface Type {
    val type: BaseType
    val default: List<Any>
    fun property(name: String, ctx: CompilerContext) {
        throw IllegalArgumentException("Property $name on $this is not supported")
    }
}

object ByteType : Type {
    override val type: BaseType
        get() = BaseType.BYTE
    override val default: List<Any>
        get() = listOf(0)
}

object IntType : Type {
    override val type: BaseType
        get() = BaseType.INT
    override val default: List<Any>
        get() = listOf(0)
}

object FloatType : Type {
    override val type: BaseType
        get() = BaseType.FLOAT
    override val default: List<Any>
        get() = listOf(0f)
}

object StringType : Type {
    override val type: BaseType
        get() = BaseType.STRING
    override val default: List<Any>
        get() = listOf("")
}

object BooleanType : Type {
    override val type: BaseType
        get() = BaseType.BOOLEAN
    override val default: List<Any>
        get() = listOf(false)
}

data class PairType(val first: Type, val second: Type) : Type {
    override val type: BaseType
        get() = BaseType.PAIR
    override val default: List<Any>
        get() = listOf(0, 0)
}

data class SliceType(val derived: Type) : Type {
    override val type: BaseType
        get() = BaseType.SLICE
    override val default: List<Any>
        get() = listOf(0)
}

data class FunctionType(val derived: Type) : Type {
    override val type: BaseType
        get() = BaseType.FUNCTION
    override val default: List<Any>
        get() = listOf(0)
}

object VoidType : Type {
    override val type: BaseType
        get() = BaseType.VOID
    override val default: List<Any>
        get() = emptyList()
}

fun BaseType.getDefaultNode(): Node {
    return when (this) {
        BaseType.BYTE -> IntNode(0)
        BaseType.INT -> IntNode(0)
        BaseType.FLOAT -> DoubleNode(0.0)
        BaseType.STRING -> StrNode("")
        BaseType.BOOLEAN -> BoolNode(false)
        BaseType.PAIR -> PairNode(first = VoidNode(), second = null)
        BaseType.SLICE -> ListNode(listOf = emptyList(), VoidType)
        BaseType.FUNCTION -> IntNode(0)
        BaseType.VOID -> ProgramNode(emptyList())
    }
}

class ObjValue(val value: Any) : Value<Any>(value) {
    override fun property(name: String, args: List<Value<*>>?): Value<*> {
        return when (name) {
            "hash" -> IntValue(value.hashCode())
            else -> super.property(name, args)
        }
    }
}

fun Value<*>.toInt(): Int {
    return when (this) {
        is IntValue -> this.value
        is DoubleValue -> this.value.toInt()
        else -> this.value().toString().toIntOrNull() ?: 0
    }
}

operator fun <T> Value<T>.compareTo(b: Value<*>): Int {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> value.compareTo(b.value)
            is DoubleValue -> value.compareTo(b.value)
            else -> value.compareTo(b.value().toString().toIntOrNull() ?: 0)
        }

        is DoubleValue -> when (b) {
            is IntValue -> value.compareTo(b.value)
            is DoubleValue -> value.compareTo(b.value)
            else -> value.compareTo(b.value().toString().toDoubleOrNull() ?: 0.0)
        }

        is StrValue -> when (b) {
            is IntValue -> (value.toDoubleOrNull() ?: 0.0).compareTo(b.value)
            is DoubleValue -> (value.toDoubleOrNull() ?: 0.0).compareTo(b.value)
            else -> value.compareTo(b.value().toString())
        }

        b -> 0
        else -> 1
    }
}

fun Value<*>.asBool(): Boolean {
    return (this as? BoolValue)?.value() ?: throw IllegalArgumentException("Expected boolean but got $this")
}

operator fun Value<*>.plus(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> IntValue(value + b.value)
            is DoubleValue -> DoubleValue(value + b.value)
            is StrValue -> StrValue(value.toString() + b.value)
            else -> StrValue(value.toString() + b.value().toString())
        }

        is DoubleValue -> when (b) {
            is IntValue -> DoubleValue(value + b.value)
            is DoubleValue -> DoubleValue(value + b.value)
            is StrValue -> StrValue(value.toString() + b.value)
            else -> StrValue(value.toString() + b.value().toString())
        }

        is StrValue -> when (b) {
            is IntValue -> StrValue(value + b.value)
            is DoubleValue -> StrValue(value + b.value)
            is StrValue -> StrValue(value + b.value)
            else -> StrValue(value + b.value().toString())
        }

        is ListValue -> when (b) {
            is ListValue -> ListValue(list + b.list)
            else -> ListValue(list + b)
        }

        else -> StrValue(value().toString() + b.value().toString())
    }
}

operator fun Value<*>.minus(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> IntValue(value - b.value)
            is DoubleValue -> DoubleValue(value - b.value)
            else -> IntValue(value - (b.value().toString().toIntOrNull() ?: 0))
        }

        is DoubleValue -> when (b) {
            is IntValue -> DoubleValue(value - b.value)
            is DoubleValue -> DoubleValue(value - b.value)
            else -> DoubleValue(value - (b.value().toString().toDoubleOrNull() ?: 0.0))
        }

        is StrValue -> StrValue(value.replace(b.value().toString(), ""))
        else -> this
    }
}

operator fun Value<*>.times(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> IntValue(value * b.value)
            is DoubleValue -> DoubleValue(value * b.value)
            is StrValue -> StrValue(b.value.repeat(value))
            else -> IntValue(value * (b.value().toString().toIntOrNull() ?: 0))
        }

        is DoubleValue -> when (b) {
            is IntValue -> DoubleValue(value * b.value)
            is DoubleValue -> DoubleValue(value * b.value)
            is StrValue -> StrValue(b.value.repeat(value.toInt()))
            else -> DoubleValue(value * (b.value().toString().toDoubleOrNull() ?: 0.0))
        }

        is StrValue -> when (b) {
            is IntValue -> StrValue(value.repeat(b.value))
            is DoubleValue -> StrValue(value.repeat(b.value.toInt()))
            else -> this
        }

        else -> this
    }
}

private fun Int.throwIfZero(): Int {
    return if (this == 0) throw IllegalArgumentException("Division by zero") else this
}

private fun Double.throwIfZero(): Double {
    return if (this == 0.0) throw IllegalArgumentException("Division by zero") else this
}

operator fun Value<*>.div(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> DoubleValue(value.toDouble() / b.value.throwIfZero())
            is DoubleValue -> DoubleValue(value / b.value.throwIfZero())
            else -> IntValue(value / (b.value().toString().toIntOrNull() ?: 0).throwIfZero())
        }

        is DoubleValue -> when (b) {
            is IntValue -> DoubleValue(value / b.value.throwIfZero())
            is DoubleValue -> DoubleValue(value / b.value.throwIfZero())
            else -> DoubleValue(value / (b.value().toString().toDoubleOrNull() ?: 0.0).throwIfZero())
        }

        is StrValue -> when (b) {
            is IntValue -> ListValue(value.chunked(b.value).map { StrValue(it) })
            is DoubleValue -> ListValue(value.chunked(b.value.toInt()).map { StrValue(it) })
            else -> IntValue(
                (value.length - value.replace(b.value().toString(), "").length) / b.value()
                    .toString().length.throwIfZero()
            )
        }

        else -> this
    }
}

operator fun Value<*>.rem(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> DoubleValue(value.toDouble() % b.value.throwIfZero())
            is DoubleValue -> DoubleValue(value % b.value.throwIfZero())
            else -> IntValue(value % (b.value().toString().toIntOrNull() ?: 0).throwIfZero())
        }

        is DoubleValue -> when (b) {
            is IntValue -> DoubleValue(value % b.value.throwIfZero())
            is DoubleValue -> DoubleValue(value % b.value.throwIfZero())
            else -> DoubleValue(value % (b.value().toString().toDoubleOrNull() ?: 0.0).throwIfZero())
        }

        is StrValue -> when (b) {
            is IntValue -> IntValue(value.length % b.value.throwIfZero())
            is DoubleValue -> IntValue(value.length % b.value().toInt().throwIfZero())
            else -> IntValue(value.length % b.value().toString().length.throwIfZero())
        }

        else -> this
    }
}