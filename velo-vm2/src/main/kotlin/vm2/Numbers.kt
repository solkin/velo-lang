package vm2

import core.DataClassInfo

/**
 * Numeric promotion, comparison, structural equality and hashing.
 *
 * The instruction set has one set of arithmetic ops for all numeric kinds;
 * promotion is by value along the rank `Float > Long > Int`: if either operand
 * is a `Float` the operation is in float, otherwise if either is a `Long` it is
 * in long, otherwise integer (a `Byte` participates as its int value — byte
 * values mostly originate from string indexing and flow as ints).
 */
object Numbers {

    private fun isFloat(v: Any?) = v is Float || v is Double
    private fun isLong(v: Any?) = v is Long

    private fun toInt(v: Any?): Int = when (v) {
        is Int -> v
        is Byte -> v.toInt()
        is Long -> v.toInt()
        is Float -> v.toInt()
        is Boolean -> if (v) 1 else 0
        else -> throw IllegalStateException("Not an integer value: $v")
    }

    private fun toLong(v: Any?): Long = when (v) {
        is Long -> v
        is Int -> v.toLong()
        is Byte -> v.toLong()
        is Float -> v.toLong()
        is Boolean -> if (v) 1L else 0L
        else -> throw IllegalStateException("Not a long value: $v")
    }

    private fun toFloat(v: Any?): Float = when (v) {
        is Float -> v
        is Int -> v.toFloat()
        is Byte -> v.toFloat()
        is Long -> v.toFloat()
        else -> throw IllegalStateException("Not a float value: $v")
    }

    // Int+Int is by far the hottest case (loop counters, indices, integer math),
    // so each op tries it first — two `is Int` checks and a direct primitive op —
    // before falling back to the mixed/long/float promotion path.
    fun add(a: Any?, b: Any?): Any = if (a is Int && b is Int) a + b else addSlow(a, b)
    fun sub(a: Any?, b: Any?): Any = if (a is Int && b is Int) a - b else subSlow(a, b)
    fun mul(a: Any?, b: Any?): Any = if (a is Int && b is Int) a * b else mulSlow(a, b)
    fun div(a: Any?, b: Any?): Any = if (a is Int && b is Int) a / b else divSlow(a, b)
    fun rem(a: Any?, b: Any?): Any = if (a is Int && b is Int) a % b else remSlow(a, b)

    private fun addSlow(a: Any?, b: Any?): Any =
        if (isFloat(a) || isFloat(b)) toFloat(a) + toFloat(b)
        else if (isLong(a) || isLong(b)) toLong(a) + toLong(b)
        else toInt(a) + toInt(b)
    private fun subSlow(a: Any?, b: Any?): Any =
        if (isFloat(a) || isFloat(b)) toFloat(a) - toFloat(b)
        else if (isLong(a) || isLong(b)) toLong(a) - toLong(b)
        else toInt(a) - toInt(b)
    private fun mulSlow(a: Any?, b: Any?): Any =
        if (isFloat(a) || isFloat(b)) toFloat(a) * toFloat(b)
        else if (isLong(a) || isLong(b)) toLong(a) * toLong(b)
        else toInt(a) * toInt(b)
    private fun divSlow(a: Any?, b: Any?): Any =
        if (isFloat(a) || isFloat(b)) toFloat(a) / toFloat(b)
        else if (isLong(a) || isLong(b)) toLong(a) / toLong(b)
        else toInt(a) / toInt(b)
    private fun remSlow(a: Any?, b: Any?): Any =
        if (isFloat(a) || isFloat(b)) toFloat(a) % toFloat(b)
        else if (isLong(a) || isLong(b)) toLong(a) % toLong(b)
        else toInt(a) % toInt(b)

    /** `Op.More`: `[a, b] -> [a > b]`. */
    fun more(a: Any?, b: Any?): Boolean =
        if (a is Int && b is Int) a > b
        else if (isFloat(a) || isFloat(b)) toFloat(a) > toFloat(b)
        else if (isLong(a) || isLong(b)) toLong(a) > toLong(b)
        else toInt(a) > toInt(b)

    // Bitwise/shift ops stay in long when a long is involved, else int. A shift's
    // width is taken from the left operand's kind; the shift amount is an int.
    fun and(a: Any?, b: Any?): Any = if (isLong(a) || isLong(b)) toLong(a) and toLong(b) else toInt(a) and toInt(b)
    fun or(a: Any?, b: Any?): Any = if (isLong(a) || isLong(b)) toLong(a) or toLong(b) else toInt(a) or toInt(b)
    fun xor(a: Any?, b: Any?): Any = if (isLong(a) || isLong(b)) toLong(a) xor toLong(b) else toInt(a) xor toInt(b)
    fun shl(a: Any?, b: Any?): Any = if (a is Long) a shl toInt(b) else toInt(a) shl toInt(b)
    fun shr(a: Any?, b: Any?): Any = if (a is Long) a shr toInt(b) else toInt(a) shr toInt(b)

    fun intInt(v: Any?): Int = toInt(v)

    fun longLong(v: Any?): Long = toLong(v)

    fun floatFloat(v: Any?): Float = toFloat(v)

    /** `Op.Equals`: deep structural equality of any two values. */
    fun equals(a: Any?, b: Any?, dataClasses: Map<Int, DataClassInfo>): Boolean {
        if (a is Int && b is Int) return a == b
        if (a is VArray && b is VArray) {
            if (a.size != b.size) return false
            for (i in 0 until a.size) if (!equals(a.data[i], b.data[i], dataClasses)) return false
            return true
        }
        if (a is Instance && b is Instance) {
            if (a.frameNum != b.frameNum) return false
            val info = dataClasses[a.frameNum] ?: return a === b
            return info.fields.all { equals(a.scope.load(it.index), b.scope.load(it.index), dataClasses) }
        }
        if (a is Number && b is Number) {
            return if (isFloat(a) || isFloat(b)) toFloat(a) == toFloat(b)
            else if (isLong(a) || isLong(b)) toLong(a) == toLong(b)
            else toInt(a) == toInt(b)
        }
        return a == b
    }

    /**
     * `Op.Hash`: a fixed, platform-independent hash (the JVM `hashCode`
     * algorithm spelled out, not delegated to the host) so the value is
     * identical on every VM backend. The stdlib `Map` buckets on this, so a
     * port that recomputed it differently would change iteration order and
     * diverge from the golden output.
     */
    fun hash(v: Any?): Int = when (v) {
        null -> 0
        is Int -> v
        is Byte -> v.toInt()
        is Long -> (v xor (v ushr 32)).toInt()
        is Boolean -> if (v) 1231 else 1237
        is Float -> v.toRawBits()
        is String -> { var h = 0; for (c in v) h = 31 * h + c.code; h }
        is VArray -> { var h = 1; for (e in v.data) h = 31 * h + hash(e); h }
        else -> v.hashCode()
    }

    /** Code point → string (`Op.IntChar`), with astral-plane surrogate handling — no host helper. */
    fun codePointToString(cp: Int): String =
        if (cp in 0..0xFFFF) {
            cp.toChar().toString()
        } else {
            val c = cp - 0x10000
            charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
        }
}
