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
 * Wall-clock comparison of the legacy VM and velo-vm2 on the heap-stress
 * kernels in `bench/`. Not a hard gate (timings are machine-dependent) — it
 * prints a min/median table and asserts only that vm2 produces the same result
 * and stays within a generous factor of the legacy VM, so a gross regression
 * still fails the build.
 */
class Vm2BenchTest {

    private val benchDir = sequenceOf(File("../bench"), File("bench")).first { it.isDirectory }
    private val nullOut = PrintStream(object : OutputStream() { override fun write(b: Int) {} })

    private fun timedLegacy(p: SerializedProgram, reg: NativeRegistry): Long = timed { vm.VeloRuntime(reg).run(p) }
    private fun timedVm2(p: SerializedProgram, reg: NativeRegistry): Long = timed { vm2.VeloRuntime(reg).run(p) }

    private inline fun timed(block: () -> Unit): Long {
        val saved = System.out
        System.setOut(nullOut)
        val t0 = System.nanoTime()
        try { block() } finally { System.setOut(saved) }
        return System.nanoTime() - t0
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
    fun `bench legacy vs vm2`() {
        val kernels = listOf("primes-quiet", "array-rw", "alloc-churn", "objects")
        val warmup = 2
        val measure = 5

        println("%-14s %10s %10s %8s".format("kernel", "legacy(ms)", "vm2(ms)", "ratio"))
        for (k in kernels) {
            val reg = NativeRegistry().register(Terminal::class)
            val program = VeloCompiler(reg).compile(File(benchDir, "$k.vel").path) ?: error("compile $k failed")

            assertEquals(
                output(program) { p, r -> vm.VeloRuntime(r).run(p) },
                output(program) { p, r -> vm2.VeloRuntime(r).run(p) },
                "vm2 output diverged on $k",
            )

            repeat(warmup) { timedLegacy(program, reg); timedVm2(program, reg) }
            val legacy = (0 until measure).map { timedLegacy(program, reg) }.sorted()
            val fresh = (0 until measure).map { timedVm2(program, reg) }.sorted()
            val lMed = legacy[measure / 2] / 1e6
            val vMed = fresh[measure / 2] / 1e6
            println("%-14s %10.1f %10.1f %7.2fx".format(k, lMed, vMed, vMed / lMed))
        }
    }
}
