package vm

/**
 * Numeric promotion by **rank** — `Float > Long > Int` — applied regardless of
 * which side each operand is on (a `Byte` participates as its `Int` value). The
 * instruction set has one arithmetic op per operation for every numeric kind;
 * this is where a mixed pair is promoted to the wider type before the primitive
 * operation runs.
 *
 * Kept in step with velo-vm2's `Numbers`, the reference both VMs are pinned
 * against by the `/conformance` corpus. Promoting by the wider operand (not by
 * the left one) is what makes `0 - aLong` stay a long, and comparing/hashing by
 * value is what makes `aLong == 3` true.
 */

private fun isFloatKind(v: Number) = v is Float || v is Double
private fun isLongKind(v: Number) = v is Long

fun Number.plus(b: Number): Number =
    if (isFloatKind(this) || isFloatKind(b)) this.toFloat() + b.toFloat()
    else if (isLongKind(this) || isLongKind(b)) this.toLong() + b.toLong()
    else this.toInt() + b.toInt()

fun Number.minus(b: Number): Number =
    if (isFloatKind(this) || isFloatKind(b)) this.toFloat() - b.toFloat()
    else if (isLongKind(this) || isLongKind(b)) this.toLong() - b.toLong()
    else this.toInt() - b.toInt()

fun Number.multiply(b: Number): Number =
    if (isFloatKind(this) || isFloatKind(b)) this.toFloat() * b.toFloat()
    else if (isLongKind(this) || isLongKind(b)) this.toLong() * b.toLong()
    else this.toInt() * b.toInt()

fun Number.divide(b: Number): Number =
    if (isFloatKind(this) || isFloatKind(b)) this.toFloat() / b.toFloat()
    else if (isLongKind(this) || isLongKind(b)) this.toLong() / b.toLong()
    else this.toInt() / b.toInt()

/** `Op.Rem`: remainder in the promoted type (int/long/float), not int-only. */
fun Number.remainder(b: Number): Number =
    if (isFloatKind(this) || isFloatKind(b)) this.toFloat() % b.toFloat()
    else if (isLongKind(this) || isLongKind(b)) this.toLong() % b.toLong()
    else this.toInt() % b.toInt()

/** `Op.More`: greater-than in the promoted type, not int-only. */
fun Number.greaterThan(b: Number): Boolean =
    if (isFloatKind(this) || isFloatKind(b)) this.toFloat() > b.toFloat()
    else if (isLongKind(this) || isLongKind(b)) this.toLong() > b.toLong()
    else this.toInt() > b.toInt()

/** `Op.Equals` numeric case: two numbers are equal by value after promotion. */
fun numericEquals(a: Number, b: Number): Boolean =
    if (isFloatKind(a) || isFloatKind(b)) a.toFloat() == b.toFloat()
    else if (isLongKind(a) || isLongKind(b)) a.toLong() == b.toLong()
    else a.toInt() == b.toInt()

/**
 * `Op.NumConv`: convert a numeric value to the target kind. The result is fixed by
 * [to] alone (the `from` operand is informational); the source is read
 * polymorphically. Narrowing to int/long truncates toward zero, to byte takes
 * the low 8 bits — matching the former per-pair conversion opcodes.
 */
fun Number.convertTo(to: core.VmType): Number = when (to) {
    core.VmType.Int -> this.toInt()
    core.VmType.Long -> this.toLong()
    core.VmType.Float -> this.toFloat()
    core.VmType.Byte -> this.toInt().toByte()
    else -> throw IllegalStateException("Op.NumConv: not a numeric target: $to")
}

/** `Op.NumStr`: decimal string of a numeric value (byte/int/long plain, float with a fraction). */
fun numStr(v: Number): String = if (v is Byte) v.toInt().toString() else v.toString()

/**
 * `Op.StrNum`: parse a decimal string into the target numeric kind, trimming
 * surrounding whitespace. Host-backed (a correct float parse needs the platform
 * routine); an invalid string throws, as `.toInt()`/`.toFloat()` do.
 */
fun strNum(s: String, to: core.VmType): Number = when (to) {
    core.VmType.Int -> s.trim().toInt()
    core.VmType.Long -> s.trim().toLong()
    core.VmType.Float -> s.trim().toFloat()
    else -> throw IllegalStateException("Op.StrNum: not a numeric target: $to")
}
