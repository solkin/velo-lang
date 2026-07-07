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
