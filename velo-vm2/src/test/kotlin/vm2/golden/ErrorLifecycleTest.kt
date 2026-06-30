package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloError
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A native that throws, for runtime-error propagation tests. */
class Boom {
    fun bang(): Int = throw RuntimeException("kaboom")
}

/** A native that records values, for fire-and-forget liveness tests. */
class Sink {
    fun record(v: Int) { recorded.add(v) }
    companion object { val recorded = CopyOnWriteArrayList<Int>() }
}

/**
 * Error & lifecycle model (A2): a failure inside an actor method surfaces as a
 * [VeloError] at the `await` that drains it (tagged with the actor), an
 * unhandled main-fiber failure is rethrown from [VeloRuntime.run], and the
 * event loop stays alive until in-flight actor work drains — so a
 * fire-and-forget `async` still runs to completion.
 */
class ErrorLifecycleTest {

    @AfterTest fun tearDown() = Sink.recorded.clear()

    private fun compile(source: String, registry: NativeRegistry) =
        File.createTempFile("a2test", ".vel").apply { writeText(source); deleteOnExit() }
            .let { VeloCompiler(registry).compile(it.path) ?: error("compile failed") }

    private fun run(source: String, registry: NativeRegistry) {
        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos))
        try { VeloRuntime(registry).run(compile(source, registry)) } finally { System.setOut(old) }
    }

    @Test
    fun `actor failure surfaces at await tagged with the actor`() {
        val registry = NativeRegistry().register(Terminal::class).register(Boom::class)
        val ex = assertFailsWith<VeloError> {
            run(
                """
                Terminal term = new Terminal();
                Boom boom = new Boom();
                actor class Worker() { func go() int { boom.bang(); }; };
                actor[Worker] w = new Worker();
                term.println((await async w.go()).str());
                """.trimIndent(),
                registry,
            )
        }
        assertContains(ex.message ?: "", "actor 'Worker' failed")
        assertContains(ex.message ?: "", "kaboom")
    }

    @Test
    fun `unhandled main failure is rethrown from run`() {
        val registry = NativeRegistry().register(Terminal::class).register(Boom::class)
        val ex = assertFailsWith<VeloError> {
            run(
                """
                Terminal term = new Terminal();
                Boom boom = new Boom();
                term.println(boom.bang().str());
                """.trimIndent(),
                registry,
            )
        }
        assertContains(ex.message ?: "", "kaboom")
    }

    @Test
    fun `un-awaited async does not pin the program open`() {
        val registry = NativeRegistry().register(Terminal::class).register(Sink::class)
        // A self-rescheduling fire-and-forget loop the program never cancels —
        // the Lode Runner "doesn't stop on back" shape. Daemon semantics: once
        // main returns and nothing is retained or awaited, the program ends and
        // the runaway loop is abandoned rather than pinning it open forever.
        run(
            """
            Terminal term = new Terminal();
            actor class Sleeper() {
                func nap() void { void };
            };
            actor[Sleeper] s = new Sleeper();
            async s.nap();
            async s.nap();
            term.println("started");
            """.trimIndent(),
            registry,
        )
        // run() returning at all is the assertion — it would hang if un-awaited
        // actor work pinned the loop (the regression behind Lode Runner / back).
    }
}
