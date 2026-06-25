import compiler.VeloCompiler
import core.NativeRegistry
import core.SerializedProgram
import vm.MemoryStats
import vm.VM
import vm.VMProfiler
import java.io.OutputStream
import java.io.PrintStream

/**
 * Heap stress benchmark for the VM (scaffolding for the MemoryArea → collector
 * work). Compiles each kernel once, warms the JIT, then times repeated runs of
 * the whole program with program output suppressed. Reports min/median/p90 wall
 * time plus the heap area's allocation count and peak live-entry count.
 *
 * Run: ./gradlew :velo-cli:bench   (tune via BENCH_WARMUP / BENCH_MEASURE)
 */
private val NULL_OUT = PrintStream(object : OutputStream() {
    override fun write(b: Int) {}
    override fun write(b: ByteArray, off: Int, len: Int) {}
})

fun main() {
    val warmup = System.getenv("BENCH_WARMUP")?.toIntOrNull() ?: 5
    val measure = System.getenv("BENCH_MEASURE")?.toIntOrNull() ?: 12
    val kernels = listOf("array-rw", "alloc-churn", "objects", "primes-quiet")

    println("VM heap benchmark — warmup=$warmup measure=$measure")
    println("%-14s %9s %9s %9s %14s %12s".format("kernel", "min(ms)", "med", "p90", "allocs", "peakLive"))

    for (k in kernels) {
        val natives = NativeRegistry().registerDefaults()
        val program = VeloCompiler(natives).compile("bench/$k.vel")
        if (program == null) {
            System.err.println("compile failed: $k")
            continue
        }
        System.err.println("· $k: warmup×$warmup …")
        repeat(warmup) { runOnce(program, natives) }
        System.gc()
        Thread.sleep(50)
        System.err.println("· $k: measure×$measure …")

        val times = LongArray(measure)
        var stats: MemoryStats? = null
        for (m in 0 until measure) {
            val (ns, s) = runOnce(program, natives)
            times[m] = ns
            stats = s
        }
        times.sort()
        val ms = { ns: Long -> ns / 1_000_000.0 }
        val p90 = times[((measure * 9) / 10).coerceAtMost(measure - 1)]
        println(
            "%-14s %9.2f %9.2f %9.2f %14d %12d".format(
                k, ms(times[0]), ms(times[measure / 2]), ms(p90),
                stats?.allocations ?: -1L, stats?.peakCount ?: -1L,
            )
        )
    }
}

private fun runOnce(program: SerializedProgram, natives: NativeRegistry): Pair<Long, MemoryStats?> {
    val profiler = VMProfiler(enabled = false)
    val vm = VM(natives, profiler)
    vm.load(program)
    val saved = System.out
    System.setOut(NULL_OUT)
    val t0 = System.nanoTime()
    try {
        vm.run()
    } finally {
        System.setOut(saved)
    }
    val elapsed = System.nanoTime() - t0
    return elapsed to profiler.memoryStats
}
