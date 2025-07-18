package vm

fun Number.plus(b: Number) : Number {
    val a = this
    return when (a) {
        is Byte -> a + b.toByte()
        is Int -> a + b.toInt()
        is Float -> a + b.toFloat()
        is Long -> a + b.toLong()
        is Double -> a + b.toDouble()
        else -> throw IllegalArgumentException("Not a number")
    }
}

fun Number.minus(b: Number) : Number {
    val a = this
    return when (a) {
        is Byte -> a - b.toByte()
        is Int -> a - b.toInt()
        is Float -> a - b.toFloat()
        is Long -> a - b.toLong()
        is Double -> a - b.toDouble()
        else -> throw IllegalArgumentException("Not a number")
    }
}

fun Number.divide(b: Number) : Number {
    val a = this
    return when (a) {
        is Byte -> a / b.toByte()
        is Int -> a / b.toInt()
        is Float -> a / b.toFloat()
        is Long -> a / b.toLong()
        is Double -> a / b.toDouble()
        else -> throw IllegalArgumentException("Not a number")
    }
}

fun Number.multiply(b: Number) : Number {
    val a = this
    return when (a) {
        is Byte -> a * b.toByte()
        is Int -> a * b.toInt()
        is Float -> a * b.toFloat()
        is Long -> a * b.toLong()
        is Double -> a * b.toDouble()
        else -> throw IllegalArgumentException("Not a number")
    }
}
