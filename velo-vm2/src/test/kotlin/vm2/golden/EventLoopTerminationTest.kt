package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import core.VeloFunction
import vm2.VeloRuntime
import vm2.host.PooledDispatcherFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Sleeps, to model a Ticker actor. */
class Clock2 {
    fun sleep(ms: Int) { Thread.sleep(ms.toLong()) }
}

/** Models a UI screen: holds a retained "back" callback the test can fire, releases it on close. */
class Bus2 {
    fun register(cb: VeloFunction) { cb.retain(); captured = cb }
    fun close() { captured?.release(); captured = null }
    companion object { @Volatile var captured: VeloFunction? = null }
}

/**
 * Regression guard for event-loop termination (the Lode Runner "doesn't stop on
 * back" report). An event-driven program runs a recurring self-rescheduling
 * actor tick and is kept alive by a retained host callback (its UI screen).
 * Pressing "back" releases that retain; the program must then terminate — both
 * when back also cancels the loop (the app-bar nav path) AND when it does not
 * (the system-back path that only closes the screen). Un-awaited fire-and-forget
 * actor work must never pin the loop open on its own.
 */
class EventLoopTerminationTest {

    @AfterTest fun tearDown() { Bus2.captured = null }

    private fun program(cancelsLoop: Boolean): String {
        val backBody = if (cancelsLoop) "loopToken = loopToken + 1; bus.close();" else "bus.close();"
        return """
            Terminal term = new Terminal();
            Clock2 clock = new Clock2();
            Bus2 bus = new Bus2();

            actor class Ticker() {
                Clock2 t = new Clock2();
                func after(int ms, int token, func[(int) void] cb) void {
                    t.sleep(ms);
                    cb(token);
                    void
                };
            };
            actor[Ticker] ticker = new Ticker();

            int loopToken = 0;
            func tick(int token) void {
                if (token == loopToken) {
                    async ticker.after(15, token, func(int x) void { tick(x); void });
                };
                void
            };

            bus.register(func() void { $backBody void });

            loopToken = loopToken + 1;
            async ticker.after(15, loopToken, func(int x) void { tick(x); void });
            term.println("running");
        """.trimIndent()
    }

    private fun assertTerminatesAfterBack(cancelsLoop: Boolean) {
        val registry = NativeRegistry()
            .register(Terminal::class).register(Clock2::class).register(Bus2::class)
        val program = VeloCompiler(registry).compile(
            File.createTempFile("evloop", ".vel").apply { writeText(program(cancelsLoop)); deleteOnExit() }.path
        ) ?: error("compile failed")

        val done = Thread {
            val baos = ByteArrayOutputStream(); val old = System.out
            System.setOut(PrintStream(baos))
            try {
                VeloRuntime(registry).actorPlacement { PooledDispatcherFactory() }.run(program)
            } finally { System.setOut(old) }
        }.apply { isDaemon = true; start() }

        Thread.sleep(120) // let the loop tick a few times
        (Bus2.captured ?: error("back callback never registered")).post()

        done.join(3000)
        assertTrue(!done.isAlive, "program did not terminate after back (cancelsLoop=$cancelsLoop) — loop stayed pinned")
    }

    @Test fun `terminates when back cancels the loop`() = assertTerminatesAfterBack(cancelsLoop = true)

    @Test fun `terminates when back only closes the screen`() = assertTerminatesAfterBack(cancelsLoop = false)
}
