package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import core.VeloFunction
import vm2.VeloRuntime
import vm2.host.ThreadDispatcher
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Captures a Velo callback so the test can fire it from another thread (mirrors velo-cli's TestBridge). */
class Bridge {
    fun register(cb: VeloFunction) { cb.retain(); captured = cb }
    fun mark() { invokeThread = Thread.currentThread().name }
    fun fireFromBackground(value: Int) {
        val cb = captured ?: error("no callback")
        Thread({ cb.post(value) }, "bg-fire").start()
    }
    fun fireInline(value: Int) { (captured ?: error("no callback")).call(value).join() }
    fun release() { captured?.release(); captured = null }

    companion object {
        @Volatile var captured: VeloFunction? = null
        @Volatile var invokeThread: String? = null
        fun reset() { captured?.release(); captured = null; invokeThread = null }
    }
}

/** A native taking a Kotlin function type — the host receives a plain lambda. */
class Each {
    fun each(cb: (Int) -> Unit) { cb(1); cb(2); cb(3) }
}

/**
 * Host-side callback interop (A1): a Velo function handed to a native is a
 * [VeloFunction] that runs on its owner's dispatcher — inline when called from
 * the owner's thread, posted otherwise — with retain/release keeping the loop
 * alive for out-of-band firing; a Kotlin function-type parameter arrives as a
 * lambda. Plus the Velo-side routing rules (same-owner inline vs cross-actor post).
 */
class HostCallbackTest {

    @AfterTest fun tearDown() = Bridge.reset()

    private fun run(source: String): String {
        val velFile = File.createTempFile("hostcb", ".vel").apply { writeText(source) }
        try {
            val registry = NativeRegistry()
                .register(Terminal::class).register(Bridge::class).register(Each::class)
            val program = VeloCompiler(registry).compile(velFile.path) ?: error("compile failed")
            val baos = ByteArrayOutputStream(); val old = System.out
            System.setOut(PrintStream(baos))
            try { VeloRuntime(registry).run(program) } finally { System.setOut(old) }
            return baos.toString().trimEnd('\n')
        } finally {
            velFile.delete()
        }
    }

    @Test
    fun `native callback fired from a background thread runs on the main thread`() {
        val mainThread = Thread.currentThread().name
        val out = run(
            """
            Terminal term = new Terminal();
            Bridge bridge = new Bridge();
            bridge.register(func(int v) void {
                bridge.mark();
                term.println("native got: ".con(v.str()));
                bridge.release();
                void
            });
            bridge.fireFromBackground(21);
            term.println("fired");
            """.trimIndent()
        )
        assertEquals("fired\nnative got: 21", out)
        assertEquals(mainThread, Bridge.invokeThread)
    }

    @Test
    fun `native callback invoked inline from the owner thread`() {
        val out = run(
            """
            Terminal term = new Terminal();
            Bridge bridge = new Bridge();
            bridge.register(func(int v) void { term.println("inline: ".con(v.str())); void });
            bridge.fireInline(5);
            term.println("after inline");
            bridge.release();
            """.trimIndent()
        )
        assertEquals("inline: 5\nafter inline", out)
    }

    @Test
    fun `kotlin function-type callback receives a lambda`() {
        val out = run(
            """
            Terminal term = new Terminal();
            Each e = new Each();
            e.each(func(int v) void { term.println(v.str()); void });
            """.trimIndent()
        )
        assertEquals("1\n2\n3", out)
    }

    @Test
    fun `actor-to-actor callback executes on the owning actor`() {
        val out = run(
            """
            Terminal term = new Terminal();
            actor class Holder() {
                int n = 0;
                func makeAdder() func[(int) void] { return return func(int v) void { n = n + v; void }; };
                func value() int { return n; };
            };
            actor class Firer() {
                func fire(func[(int) void] cb, int v) void { cb(v); void };
            };
            actor[Holder] a = new Holder();
            actor[Firer] b = new Firer();
            func[(int) void] cb = await async a.makeAdder();
            await async b.fire(cb, 5);
            await async b.fire(cb, 9);
            term.println("a.n = ".con((await async a.value()).str()));
            """.trimIndent()
        )
        assertEquals("a.n = 14", out)
    }

    @Test
    fun `embedded start runs the main context and its callbacks on the injected dispatcher`() {
        val registry = NativeRegistry().register(Terminal::class).register(Bridge::class)
        val velFile = File.createTempFile("embed", ".vel").apply {
            writeText(
                """
                Terminal term = new Terminal();
                Bridge bridge = new Bridge();
                bridge.register(func(int v) void { bridge.mark(); term.println("embedded got: ".con(v.str())); void });
                term.println("embedded ready");
                """.trimIndent()
            )
            deleteOnExit()
        }
        val program = VeloCompiler(registry).compile(velFile.path) ?: error("compile failed")

        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos, true))
        val mainDispatcher = ThreadDispatcher("vm2-main")
        val handle = VeloRuntime(registry).start(program, mainDispatcher)
        try {
            val deadline = System.currentTimeMillis() + 5000
            while (Bridge.captured == null && System.currentTimeMillis() < deadline) Thread.sleep(5)
            val cb = Bridge.captured ?: error("callback never registered")
            cb.call(7).get(5, TimeUnit.SECONDS) // fired from the test thread; runs on vm2-main
        } finally {
            handle.stop()
            System.setOut(old)
        }
        assertContains(Bridge.invokeThread ?: "", "vm2-main")
        assertEquals("embedded ready\nembedded got: 7", baos.toString().trimEnd('\n'))
    }

    @Test
    fun `callback coming home unwraps to a local function`() {
        val out = run(
            """
            Terminal term = new Terminal();
            actor class Echo() {
                func give(func[(int) void] cb) func[(int) void] { return return cb; };
            };
            actor[Echo] e = new Echo();
            func[(int) void] back = await async e.give(func(int v) void {
                term.println("local: ".con(v.str()));
                void
            });
            back(7);
            term.println("after");
            """.trimIndent()
        )
        assertEquals("local: 7\nafter", out)
    }
}
