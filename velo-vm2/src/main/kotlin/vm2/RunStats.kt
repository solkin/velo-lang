package vm2

/** Summary of one program run, returned by [VeloRuntime.run]. */
data class RunStats(
    val opsExecuted: Long,
    val wallClockMillis: Long = 0,
    val memory: MemoryStats? = null,
) {
    /** Alias for [opsExecuted] — the number of bytecode instructions executed. */
    val instructions: Long get() = opsExecuted
}
