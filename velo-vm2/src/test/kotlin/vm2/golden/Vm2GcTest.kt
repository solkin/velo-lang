package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The VM-owned mark-sweep collector (B2): the allocation-churn kernels allocate
 * hundreds of thousands of short-lived arrays / instances; with [VeloRuntime
 * .managedHeap] the live count stays bounded to the working set (garbage is
 * reclaimed) while the cumulative allocation count keeps climbing — and the
 * program result is unchanged, proving the sweep never freed a reachable object.
 */
class Vm2GcTest {

    private val benchDir = sequenceOf(File("../bench"), File("bench")).first { it.isDirectory }

    private fun compile(name: String) =
        NativeRegistry().register(Terminal::class).let { reg ->
            reg to (VeloCompiler(reg).compile(File(benchDir, "$name.vel").path) ?: error("compile $name failed"))
        }

    private fun capture(block: () -> Unit): String {
        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos))
        try { block() } finally { System.setOut(old) }
        return baos.toString().trim()
    }

    private fun assertBoundedHeap(kernel: String) {
        val (reg, program) = compile(kernel)

        val plain = capture { VeloRuntime(reg).run(program) }

        val threshold = 20_000
        var stats: vm2.RunStats? = null
        val managed = capture { stats = VeloRuntime(reg).managedHeap(thresholdAllocs = threshold).run(program) }

        val mem = stats!!.memory!!
        assertEquals(plain, managed, "managed-heap result diverged on $kernel")
        assertTrue(mem.allocations > 100_000, "$kernel: expected heavy allocation, got ${mem.allocations}")
        assertTrue(mem.collections > 3, "$kernel: collector barely ran (${mem.collections})")
        // The live set is capped near the collection threshold instead of
        // climbing with the (much larger) cumulative allocation count.
        assertTrue(
            mem.peakLive < threshold * 2 && mem.peakLive < mem.allocations / 3,
            "$kernel: live set not bounded — peakLive=${mem.peakLive}, allocations=${mem.allocations}",
        )
        println("$kernel: allocations=${mem.allocations} peakLive=${mem.peakLive} collections=${mem.collections}")
    }

    @Test fun `arrays are reclaimed`() = assertBoundedHeap("alloc-churn")

    @Test fun `instances are reclaimed`() = assertBoundedHeap("objects")

    @Test fun `actor allocations are reclaimed across the multi-actor boundary`() {
        // Previously the managed collector self-disabled once an actor spawned
        // (single-fiber roots were incomplete). Now a spawned actor that churns
        // arrays still gets its garbage swept: allocation happens inside the
        // actor's own fibers, yet the live set stays bounded.
        val reg = NativeRegistry().register(Terminal::class)
        val src = """
            Terminal term = new Terminal();
            actor class Churner() {
                func run(int rounds) int {
                    int sum = 0;
                    int i = 0;
                    while (i < rounds) {
                        array[int] tmp = new array[int](8);
                        tmp[0] = i;
                        sum += tmp[0];
                        i += 1;
                    };
                    return sum;
                };
            };
            actor[Churner] c = new Churner();
            term.println((await async c.run(300000)).str());
        """.trimIndent()
        val tmp = File.createTempFile("gc-actor", ".vel").apply { writeText(src); deleteOnExit() }
        val program = VeloCompiler(reg).compile(tmp.path) ?: error("compile failed")

        val plain = capture { VeloRuntime(reg).run(program) }
        var stats: vm2.RunStats? = null
        val managed = capture { stats = VeloRuntime(reg).managedHeap(thresholdAllocs = 20_000).run(program) }

        val mem = stats!!.memory!!
        assertEquals(plain, managed, "managed-heap result diverged with an actor present")
        assertTrue(mem.allocations > 100_000, "expected heavy allocation, got ${mem.allocations}")
        assertTrue(mem.collections > 3, "collector barely ran (${mem.collections})")
        assertTrue(mem.peakLive < 20_000 * 2, "live set not bounded with actors: peakLive=${mem.peakLive}")
        println("actor-churn: allocations=${mem.allocations} peakLive=${mem.peakLive} collections=${mem.collections}")
    }
}
