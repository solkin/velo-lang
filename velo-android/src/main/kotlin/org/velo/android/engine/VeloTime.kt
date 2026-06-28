package org.velo.android.engine

/**
 * Host implementation of the Velo `Time` native (registered under the name `Time`).
 * Same surface as the CLI's `Time`; `print` takes the `Terminal` native, which on
 * Android is [AndroidTerminal].
 */
class VeloTime {
    fun sleep(millis: Int) {
        Thread.sleep(millis.toLong())
    }

    fun unix(): Int {
        return (System.currentTimeMillis() / 1000).toInt()
    }

    // Monotonic millisecond clock for benchmarking. Counts from a fixed origin
    // captured at class load, so the value starts near zero and won't overflow a
    // 32-bit Velo int for ~24 days — measure elapsed time as a difference of two
    // millis() reads.
    fun millis(): Int {
        return ((System.nanoTime() - ORIGIN_NANOS) / 1_000_000L).toInt()
    }

    fun print(term: AndroidTerminal) {
        term.print(unix().toString())
    }

    companion object {
        private val ORIGIN_NANOS = System.nanoTime()
    }
}
