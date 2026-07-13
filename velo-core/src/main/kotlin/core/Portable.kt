package core

/**
 * Host-independent primitives that every VM backend — and every future port
 * (C / WASM / JS) — must compute identically, because their results are
 * observable and pinned by the golden output: the stdlib `Map` buckets on
 * [Hashing], and constructing a string from a code point must agree
 * byte-for-byte. They live here, in the shared contract, rather than copied into
 * each backend, so they cannot silently drift — a backend that delegated any of
 * these to the host (`value.hashCode()`, a 16-bit `Char` cast) would diverge on
 * a non-JVM port the moment a long/string/astral code point showed up.
 */

/** A Unicode code point to a 1-char string, pairing surrogates for the astral planes. */
fun codePointToString(cp: Int): String =
    if (cp in 0..0xFFFF) {
        cp.toChar().toString()
    } else {
        val c = cp - 0x10000
        charArrayOf((0xD800 + (c shr 10)).toChar(), (0xDC00 + (c and 0x3FF)).toChar()).concatToString()
    }

/**
 * The pinned structural-hash algorithm (the JVM `hashCode` spelled out, not
 * delegated to the host). [scalar] hashes a leaf value, or returns `null` for a
 * container the backend must walk itself — its array/instance representation is
 * VM-specific, so the backend folds the element/field hashes with [combine]
 * starting from [SEQ_SEED]. This keeps the numeric constants (the 31 multiplier,
 * `1231`/`1237`, `toRawBits`, the long fold) in one place across all backends.
 */
object Hashing {
    const val SEQ_SEED = 1

    fun scalar(v: Any?): Int? = when (v) {
        null -> 0
        is Int -> v
        is Byte -> v.toInt()
        is Long -> (v xor (v ushr 32)).toInt()
        is Boolean -> if (v) 1231 else 1237
        is Float -> v.toRawBits()
        is String -> { var h = 0; for (c in v) h = 31 * h + c.code; h }
        else -> null
    }

    fun combine(acc: Int, elementHash: Int): Int = 31 * acc + elementHash
}
