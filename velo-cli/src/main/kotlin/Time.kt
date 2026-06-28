class Time {
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

    companion object {
        private val ORIGIN_NANOS = System.nanoTime()
    }
    fun print(term: Terminal) {
        term.print(unix().toString())
    }
}