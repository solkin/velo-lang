package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloRuntime
import vm2.host.ThreadPerActorFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Native probe: reports the running thread and can block it. */
class Probe {
    fun threadName(): String = Thread.currentThread().name
    fun sleep(ms: Int) { Thread.sleep(ms.toLong()) }
}

/**
 * Proves the pluggable threaded actor model: with [ThreadPerActorFactory] each
 * spawned actor runs on its own host thread, so two actors execute in parallel
 * and report distinct thread names — while the same program stays correct on
 * the cooperative (no-threads) default.
 */
class ConcurrencyTest {

    private val source = """
        Terminal term = new Terminal();
        Probe probe = new Probe();

        actor class Worker(int id) {
            func run() str {
                probe.sleep(120);
                probe.threadName().con(":").con((id * 10).str);
            };
        };

        actor[Worker] a = new Worker(1);
        actor[Worker] b = new Worker(2);
        future[str] fa = async a.run();
        future[str] fb = async b.run();
        term.println(await fa);
        term.println(await fb);
    """.trimIndent()

    private fun compileAndRun(threaded: Boolean): Pair<List<String>, Long> {
        val file = File.createTempFile("concurrency", ".vel").apply { writeText(source); deleteOnExit() }
        val registry = NativeRegistry().register(Terminal::class).register(Probe::class)
        val program = VeloCompiler(registry).compile(file.path) ?: error("compile failed")

        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos))
        val start = System.currentTimeMillis()
        try {
            val runtime = VeloRuntime(registry)
            if (threaded) runtime.actorPlacement { ThreadPerActorFactory() }
            runtime.run(program)
        } finally {
            System.setOut(old)
        }
        val elapsed = System.currentTimeMillis() - start
        return baos.toString().trim().lines() to elapsed
    }

    @Test
    fun `actors run in parallel on their own threads`() {
        val (lines, elapsed) = compileAndRun(threaded = true)
        assertEquals(2, lines.size, "expected two result lines, got $lines")
        val (nameA, valA) = lines[0].split(":")
        val (nameB, valB) = lines[1].split(":")

        assertEquals("10", valA)
        assertEquals("20", valB)
        assertTrue(nameA.startsWith("velo-actor-"), "actor A ran off-thread: $nameA")
        assertTrue(nameB.startsWith("velo-actor-"), "actor B ran off-thread: $nameB")
        assertTrue(nameA != nameB, "actors shared a thread: $nameA")
        // Two 120ms sleeps overlap → well under the 240ms a serial run would take.
        assertTrue(elapsed < 200, "actors did not run in parallel (took ${elapsed}ms)")
    }

    @Test
    fun `same program is correct on the cooperative default`() {
        val (lines, _) = compileAndRun(threaded = false)
        assertEquals(2, lines.size)
        assertEquals("10", lines[0].substringAfter(":"))
        assertEquals("20", lines[1].substringAfter(":"))
    }
}
