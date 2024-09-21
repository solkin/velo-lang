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
    SLICE("slice"),
    FUNCTION("fn"),
    VOID("void"),
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
        BaseType.SLICE -> SliceNode(listOf = emptyList(), VoidType)
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
        is FloatValue -> this.value.toInt()
        else -> this.value().toString().toIntOrNull() ?: 0
    }
}

operator fun <T> Value<T>.compareTo(b: Value<*>): Int {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> value.compareTo(b.value)
            is FloatValue -> value.compareTo(b.value)
            else -> value.compareTo(b.value().toString().toIntOrNull() ?: 0)
        }

        is FloatValue -> when (b) {
            is IntValue -> value.compareTo(b.value)
            is FloatValue -> value.compareTo(b.value)
            else -> value.compareTo(b.value().toString().toDoubleOrNull() ?: 0.0)
        }

        is StringValue -> when (b) {
            is IntValue -> (value.toDoubleOrNull() ?: 0.0).compareTo(b.value)
            is FloatValue -> (value.toDoubleOrNull() ?: 0.0).compareTo(b.value)
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
            is FloatValue -> FloatValue(value + b.value)
            is StringValue -> StringValue(value.toString() + b.value)
            else -> StringValue(value.toString() + b.value().toString())
        }

        is FloatValue -> when (b) {
            is IntValue -> FloatValue(value + b.value)
            is FloatValue -> FloatValue(value + b.value)
            is StringValue -> StringValue(value.toString() + b.value)
            else -> StringValue(value.toString() + b.value().toString())
        }

        is StringValue -> when (b) {
            is IntValue -> StringValue(value + b.value)
            is FloatValue -> StringValue(value + b.value)
            is StringValue -> StringValue(value + b.value)
            else -> StringValue(value + b.value().toString())
        }

        is SliceValue -> when (b) {
            is SliceValue -> SliceValue(list + b.list)
            else -> SliceValue(list + b)
        }

        else -> StringValue(value().toString() + b.value().toString())
    }
}

operator fun Value<*>.minus(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> IntValue(value - b.value)
            is FloatValue -> FloatValue(value - b.value)
            else -> IntValue(value - (b.value().toString().toIntOrNull() ?: 0))
        }

        is FloatValue -> when (b) {
            is IntValue -> FloatValue(value - b.value)
            is FloatValue -> FloatValue(value - b.value)
            else -> FloatValue(value - (b.value().toString().toDoubleOrNull() ?: 0.0))
        }

        is StringValue -> StringValue(value.replace(b.value().toString(), ""))
        else -> this
    }
}

operator fun Value<*>.times(b: Value<*>): Value<*> {
    return when (this) {
        is IntValue -> when (b) {
            is IntValue -> IntValue(value * b.value)
            is FloatValue -> FloatValue(value * b.value)
            is StringValue -> StringValue(b.value.repeat(value))
            else -> IntValue(value * (b.value().toString().toIntOrNull() ?: 0))
        }

        is FloatValue -> when (b) {
            is IntValue -> FloatValue(value * b.value)
            is FloatValue -> FloatValue(value * b.value)
            is StringValue -> StringValue(b.value.repeat(value.toInt()))
            else -> FloatValue(value * (b.value().toString().toDoubleOrNull() ?: 0.0))
        }

        is StringValue -> when (b) {
            is IntValue -> StringValue(value.repeat(b.value))
            is FloatValue -> StringValue(value.repeat(b.value.toInt()))
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
            is IntValue -> FloatValue(value.toDouble() / b.value.throwIfZero())
            is FloatValue -> FloatValue(value / b.value.throwIfZero())
            else -> IntValue(value / (b.value().toString().toIntOrNull() ?: 0).throwIfZero())
        }

        is FloatValue -> when (b) {
            is IntValue -> FloatValue(value / b.value.throwIfZero())
            is FloatValue -> FloatValue(value / b.value.throwIfZero())
            else -> FloatValue(value / (b.value().toString().toDoubleOrNull() ?: 0.0).throwIfZero())
        }

        is StringValue -> when (b) {
            is IntValue -> SliceValue(value.chunked(b.value).map { StringValue(it) })
            is FloatValue -> SliceValue(value.chunked(b.value.toInt()).map { StringValue(it) })
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
            is IntValue -> FloatValue(value.toDouble() % b.value.throwIfZero())
            is FloatValue -> FloatValue(value % b.value.throwIfZero())
            else -> IntValue(value % (b.value().toString().toIntOrNull() ?: 0).throwIfZero())
        }

        is FloatValue -> when (b) {
            is IntValue -> FloatValue(value % b.value.throwIfZero())
            is FloatValue -> FloatValue(value % b.value.throwIfZero())
            else -> FloatValue(value % (b.value().toString().toDoubleOrNull() ?: 0.0).throwIfZero())
        }

        is StringValue -> when (b) {
            is IntValue -> IntValue(value.length % b.value.throwIfZero())
            is FloatValue -> IntValue(value.length % b.value().toInt().throwIfZero())
            else -> IntValue(value.length % b.value().toString().length.throwIfZero())
        }

        else -> this
    }
}