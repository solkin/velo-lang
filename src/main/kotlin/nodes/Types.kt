package nodes

class VoidType : Type<String>("")

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