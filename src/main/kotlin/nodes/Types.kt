package nodes

import kotlin.math.pow

enum class DataType(val type: String) {
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

interface VMType {
    val type: DataType
    val default: List<Any>
}

object VMByte : VMType {
    override val type: DataType
        get() = DataType.BYTE
    override val default: List<Any>
        get() = listOf(0)
}

object VMInt : VMType {
    override val type: DataType
        get() = DataType.INT
    override val default: List<Any>
        get() = listOf(0)
}

object VMFloat : VMType {
    override val type: DataType
        get() = DataType.FLOAT
    override val default: List<Any>
        get() = listOf(0f)
}

object VMString : VMType {
    override val type: DataType
        get() = DataType.STRING
    override val default: List<Any>
        get() = listOf("")
}

object VMBoolean : VMType {
    override val type: DataType
        get() = DataType.BOOLEAN
    override val default: List<Any>
        get() = listOf(false)
}

data class VMPair(val first: VMType, val second: VMType) : VMType {
    override val type: DataType
        get() = DataType.PAIR
    override val default: List<Any>
        get() = listOf(0, 0)
}

data class VMSlice(val derived: VMType) : VMType {
    override val type: DataType
        get() = DataType.SLICE
    override val default: List<Any>
        get() = listOf(0)
}

data class VMFunction(val derived: VMType) : VMType {
    override val type: DataType
        get() = DataType.FUNCTION
    override val default: List<Any>
        get() = listOf(0)
}

object VMVoid : VMType {
    override val type: DataType
        get() = DataType.VOID
    override val default: List<Any>
        get() = emptyList()
}

private const val MASK_STEP = 4

fun DataType.mask(depth: Int = 1): Int {
    return (ordinal + 1) shl MASK_STEP * (depth - 1)
}

fun Int.unmask(depth: Int = 1): DataType {
    val o = (2.toDouble().pow(depth * MASK_STEP.toDouble()) - 1).toInt()
    val v = this and o
    val u = v shr MASK_STEP * (depth - 1)
    val index = (u - 1).takeIf { it >= 0 } ?: throw IllegalArgumentException("Mask $depth not found")
    return DataType.values()[index]
}

fun Int.derive(depth: Int, type: DataType): Int {
    return this or type.mask(depth)
}

fun DataType.getDefault(): Any {
    return when (this) {
        DataType.BYTE -> 0
        DataType.INT -> 0
        DataType.FLOAT -> 0f
        DataType.STRING -> ""
        DataType.BOOLEAN -> false
        DataType.PAIR -> 0
        DataType.SLICE -> 0
        DataType.FUNCTION -> 0
        DataType.VOID -> Unit
    }
}

fun DataType.getDefaultNode(): Node {
    return when (this) {
        DataType.BYTE -> IntNode(0)
        DataType.INT -> IntNode(0)
        DataType.FLOAT -> DoubleNode(0.0)
        DataType.STRING -> StrNode("")
        DataType.BOOLEAN -> BoolNode(false)
        DataType.PAIR -> PairNode(first = VoidNode(), second = null)
        DataType.SLICE -> ListNode(listOf = emptyList(), VMVoid)
        DataType.FUNCTION -> IntNode(0)
        DataType.VOID -> ProgramNode(emptyList())
    }
}

class ObjType(val value: Any) : Type<Any>(value) {
    override fun property(name: String, args: List<Type<*>>?): Type<*> {
        return when (name) {
            "hash" -> IntType(value.hashCode())
            else -> super.property(name, args)
        }
    }
}

fun Type<*>.toInt(): Int {
    return when (this) {
        is IntType -> this.value
        is DoubleType -> this.value.toInt()
        else -> this.value().toString().toIntOrNull() ?: 0
    }
}

operator fun <T> Type<T>.compareTo(b: Type<*>): Int {
    return when (this) {
        is IntType -> when (b) {
            is IntType -> value.compareTo(b.value)
            is DoubleType -> value.compareTo(b.value)
            else -> value.compareTo(b.value().toString().toIntOrNull() ?: 0)
        }

        is DoubleType -> when (b) {
            is IntType -> value.compareTo(b.value)
            is DoubleType -> value.compareTo(b.value)
            else -> value.compareTo(b.value().toString().toDoubleOrNull() ?: 0.0)
        }

        is StrType -> when (b) {
            is IntType -> (value.toDoubleOrNull() ?: 0.0).compareTo(b.value)
            is DoubleType -> (value.toDoubleOrNull() ?: 0.0).compareTo(b.value)
            else -> value.compareTo(b.value().toString())
        }

        b -> 0
        else -> 1
    }
}

fun Type<*>.asBool(): Boolean {
    return (this as? BoolType)?.value() ?: throw IllegalArgumentException("Expected boolean but got $this")
}

operator fun Type<*>.plus(b: Type<*>): Type<*> {
    return when (this) {
        is IntType -> when (b) {
            is IntType -> IntType(value + b.value)
            is DoubleType -> DoubleType(value + b.value)
            is StrType -> StrType(value.toString() + b.value)
            else -> StrType(value.toString() + b.value().toString())
        }

        is DoubleType -> when (b) {
            is IntType -> DoubleType(value + b.value)
            is DoubleType -> DoubleType(value + b.value)
            is StrType -> StrType(value.toString() + b.value)
            else -> StrType(value.toString() + b.value().toString())
        }

        is StrType -> when (b) {
            is IntType -> StrType(value + b.value)
            is DoubleType -> StrType(value + b.value)
            is StrType -> StrType(value + b.value)
            else -> StrType(value + b.value().toString())
        }

        is ListType -> when (b) {
            is ListType -> ListType(list + b.list)
            else -> ListType(list + b)
        }

        else -> StrType(value().toString() + b.value().toString())
    }
}

operator fun Type<*>.minus(b: Type<*>): Type<*> {
    return when (this) {
        is IntType -> when (b) {
            is IntType -> IntType(value - b.value)
            is DoubleType -> DoubleType(value - b.value)
            else -> IntType(value - (b.value().toString().toIntOrNull() ?: 0))
        }

        is DoubleType -> when (b) {
            is IntType -> DoubleType(value - b.value)
            is DoubleType -> DoubleType(value - b.value)
            else -> DoubleType(value - (b.value().toString().toDoubleOrNull() ?: 0.0))
        }

        is StrType -> StrType(value.replace(b.value().toString(), ""))
        else -> this
    }
}

operator fun Type<*>.times(b: Type<*>): Type<*> {
    return when (this) {
        is IntType -> when (b) {
            is IntType -> IntType(value * b.value)
            is DoubleType -> DoubleType(value * b.value)
            is StrType -> StrType(b.value.repeat(value))
            else -> IntType(value * (b.value().toString().toIntOrNull() ?: 0))
        }

        is DoubleType -> when (b) {
            is IntType -> DoubleType(value * b.value)
            is DoubleType -> DoubleType(value * b.value)
            is StrType -> StrType(b.value.repeat(value.toInt()))
            else -> DoubleType(value * (b.value().toString().toDoubleOrNull() ?: 0.0))
        }

        is StrType -> when (b) {
            is IntType -> StrType(value.repeat(b.value))
            is DoubleType -> StrType(value.repeat(b.value.toInt()))
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

operator fun Type<*>.div(b: Type<*>): Type<*> {
    return when (this) {
        is IntType -> when (b) {
            is IntType -> DoubleType(value.toDouble() / b.value.throwIfZero())
            is DoubleType -> DoubleType(value / b.value.throwIfZero())
            else -> IntType(value / (b.value().toString().toIntOrNull() ?: 0).throwIfZero())
        }

        is DoubleType -> when (b) {
            is IntType -> DoubleType(value / b.value.throwIfZero())
            is DoubleType -> DoubleType(value / b.value.throwIfZero())
            else -> DoubleType(value / (b.value().toString().toDoubleOrNull() ?: 0.0).throwIfZero())
        }

        is StrType -> when (b) {
            is IntType -> ListType(value.chunked(b.value).map { StrType(it) })
            is DoubleType -> ListType(value.chunked(b.value.toInt()).map { StrType(it) })
            else -> IntType(
                (value.length - value.replace(b.value().toString(), "").length) / b.value()
                    .toString().length.throwIfZero()
            )
        }

        else -> this
    }
}

operator fun Type<*>.rem(b: Type<*>): Type<*> {
    return when (this) {
        is IntType -> when (b) {
            is IntType -> DoubleType(value.toDouble() % b.value.throwIfZero())
            is DoubleType -> DoubleType(value % b.value.throwIfZero())
            else -> IntType(value % (b.value().toString().toIntOrNull() ?: 0).throwIfZero())
        }

        is DoubleType -> when (b) {
            is IntType -> DoubleType(value % b.value.throwIfZero())
            is DoubleType -> DoubleType(value % b.value.throwIfZero())
            else -> DoubleType(value % (b.value().toString().toDoubleOrNull() ?: 0.0).throwIfZero())
        }

        is StrType -> when (b) {
            is IntType -> IntType(value.length % b.value.throwIfZero())
            is DoubleType -> IntType(value.length % b.value().toInt().throwIfZero())
            else -> IntType(value.length % b.value().toString().length.throwIfZero())
        }

        else -> this
    }
}