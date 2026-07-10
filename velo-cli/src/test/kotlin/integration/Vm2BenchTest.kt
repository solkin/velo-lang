package integration

import Terminal
import core.NativeRegistry
import core.SerializedProgram
import compiler.VeloCompiler
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wall-clock comparison of the three VMs on the heap-stress
 * kernels in `bench/`. Not a hard gate (timings are machine-dependent) — it
 * prints a median table and asserts that both clean-room VMs produce the same
 * result. Timings are deliberately informational because CI hosts vary.
 */
class Vm2BenchTest {

    private val benchDir = sequenceOf(File("../bench"), File("bench")).first { it.isDirectory }
    private val nullOut = PrintStream(object : OutputStream() { override fun write(b: Int) {} })

    private fun timedLegacy(p: SerializedProgram, reg: NativeRegistry): Long = timed { vm.VeloRuntime(reg).run(p) }
    private fun timedVm2(p: SerializedProgram, reg: NativeRegistry): Long = timed { vm2.VeloRuntime(reg).run(p) }
    private fun timedVm3(p: SerializedProgram, reg: NativeRegistry): Long = timed { vm3.VeloRuntime(reg).run(p) }

    private inline fun timed(block: () -> Unit): Long {
        val saved = System.out
        System.setOut(nullOut)
        val t0 = System.nanoTime()
        try { block() } finally { System.setOut(saved) }
        return System.nanoTime() - t0
    }

    private val threadMx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    /** Bytes allocated by the current thread while running [block] (JVM proxy for
     *  GC pressure — the metric that matters on ART, where boxing is dear). */
    private inline fun allocated(block: () -> Unit): Long {
        val id = Thread.currentThread().id
        val saved = System.out
        System.setOut(nullOut)
        val before = threadMx.getThreadAllocatedBytes(id)
        try { block() } finally { System.setOut(saved) }
        return threadMx.getThreadAllocatedBytes(id) - before
    }

    private fun output(p: SerializedProgram, run: (SerializedProgram, NativeRegistry) -> Unit): String {
        val reg = NativeRegistry().register(Terminal::class)
        val baos = java.io.ByteArrayOutputStream(); val saved = System.out
        System.setOut(PrintStream(baos))
        try { run(p, reg) } finally { System.setOut(saved) }
        return baos.toString().lineSequence()
            .filterNot { it.startsWith("✓ Program") || it.startsWith("⏹ Program") || it.startsWith("Program ") }
            .joinToString("\n").trim()
    }

    @Test
    fun `bench all virtual machines`() {
        val kernels = listOf("primes-quiet", "array-rw", "alloc-churn", "objects")
        val warmup = 2
        val measure = 5

        println("%-14s %10s %10s %10s %9s".format("kernel", "legacy(ms)", "vm2(ms)", "vm3(ms)", "vm3/old"))
        for (k in kernels) {
            val reg = NativeRegistry().register(Terminal::class)
            val program = VeloCompiler(reg).compile(File(benchDir, "$k.vel").path) ?: error("compile $k failed")

            assertEquals(
                output(program) { p, r -> vm.VeloRuntime(r).run(p) },
                output(program) { p, r -> vm2.VeloRuntime(r).run(p) },
                "vm2 output diverged on $k",
            )
            assertEquals(
                output(program) { p, r -> vm.VeloRuntime(r).run(p) },
                output(program) { p, r -> vm3.VeloRuntime(r).run(p) },
                "vm3 output diverged on $k",
            )

            repeat(warmup) { timedLegacy(program, reg); timedVm2(program, reg); timedVm3(program, reg) }
            val legacy = (0 until measure).map { timedLegacy(program, reg) }.sorted()
            val fresh = (0 until measure).map { timedVm2(program, reg) }.sorted()
            val compact = (0 until measure).map { timedVm3(program, reg) }.sorted()
            val lMed = legacy[measure / 2] / 1e6
            val vMed = fresh[measure / 2] / 1e6
            val cMed = compact[measure / 2] / 1e6
            val aLegacy = allocated { vm.VeloRuntime(reg).run(program) } / 1e6
            val aVm2 = allocated { vm2.VeloRuntime(reg).run(program) } / 1e6
            val aVm3 = allocated { vm3.VeloRuntime(reg).run(program) } / 1e6
            println(
                "%-14s %10.1f %10.1f %10.1f %8.2fx   | alloc MB  legacy=%-6.0f vm2=%-6.0f vm3=%-6.0f (vm3/vm2=%.2f)"
                    .format(k, lMed, vMed, cMed, cMed / lMed, aLegacy, aVm2, aVm3, aVm3 / aVm2),
            )
        }
    }
}
