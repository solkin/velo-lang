package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Focused parity coverage for vm3's JVM boundary and retained callbacks. */
class Vm3InteropTest {
    private fun registry() = NativeRegistry()
        .register(Terminal::class)
        .register(Box::class)
        .register(KotlinCallbacks::class)
        .register(Geometry::class)
        .register(PointBus::class)
        .register(TestBridge::class)
        .registerData("Point", NativePoint::class)
        .registerData("Segment", NativeSegment::class)

    @AfterTest fun reset() = TestBridge.reset()

    @Test
    fun `native handles and arguments round trip`() {
        assertEquals(
            "say-hello\nsay-hello",
            run(
                """
                Terminal term = new Terminal();
                Box a = new Box("hello");
                Box b = a.wrap("say-");
                term.println(b.read());
                a.fill(b);
                term.println(a.read());
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `kotlin callback is delivered after native returns`() {
        assertEquals(
            "posted\n1\n3\n6",
            run(
                """
                Terminal term = new Terminal();
                KotlinCallbacks k = new KotlinCallbacks();
                int sum = 0;
                k.each(func(int v) void { sum = sum + v; term.println(sum.str()); void });
                term.println("posted");
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `kotlin callback can be forwarded through an actor`() {
        assertEquals(
            "1\n3\n6\ndone",
            run(
                """
                Terminal term = new Terminal();
                actor class Forwarder() {
                    KotlinCallbacks host = new KotlinCallbacks();
                    func each(func[(int) void] cb) void { host.each(cb); void };
                };
                int sum = 0;
                actor[Forwarder] forwarder = new Forwarder();
                await forwarder.each(func(int v) void {
                    sum += v;
                    term.println(sum.str());
                    void
                });
                term.println("done");
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `data class crosses native boundary by value`() {
        assertEquals(
            "(4, 6)\n4\n6",
            run(
                """
                Terminal term = new Terminal();
                data class Point(int x, int y) {};
                Geometry g = new Geometry();
                Point p = g.translate(new Point(1, 2), 3, 4);
                term.println(g.describe(p));
                term.println(p.x.str());
                term.println(p.y.str());
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `data class crosses posted and value returning callbacks`() {
        assertEquals(
            "(8, 10)\nposted 3 4",
            run(
                """
                Terminal term = new Terminal();
                data class Point(int x, int y) {};
                PointBus bus = new PointBus();
                bus.emit(new Point(3, 4), func(Point p) void {
                    term.println("posted ".con(p.x.str()).con(" ").con(p.y.str()));
                    void
                });
                Point mapped = bus.mapPoint(new Point(4, 5), func(Point p) Point {
                    return new Point(p.x * 2, p.y * 2);
                });
                term.println(new Geometry().describe(mapped));
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `retained callback from background returns to owner loop`() {
        val owner = Thread.currentThread().name
        assertEquals(
            "fired\ncallback 21",
            run(
                """
                Terminal term = new Terminal();
                TestBridge bridge = new TestBridge();
                bridge.register(func(int v) void {
                    bridge.mark();
                    term.println("callback ".con(v.str()));
                    bridge.release();
                    void
                });
                bridge.fireFromBackground(21);
                term.println("fired");
                """.trimIndent(),
            ),
        )
        assertEquals(owner, TestBridge.invokeThread)
    }

    @Test
    fun `callback call is inline on owner thread`() {
        assertEquals(
            "inline 5\nafter",
            run(
                """
                Terminal term = new Terminal();
                TestBridge bridge = new TestBridge();
                bridge.register(func(int v) void { term.println("inline ".con(v.str())); void });
                bridge.fireInline(5);
                term.println("after");
                bridge.release();
                """.trimIndent(),
            ),
        )
        assertTrue(TestBridge.captured == null)
    }

    private fun run(source: String): String {
        val registry = registry()
        val file = File.createTempFile("vm3-interop", ".vel").apply { writeText(source) }
        val program = try { VeloCompiler(registry).compile(file.path) ?: error("compile failed") } finally { file.delete() }
        val bytes = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(bytes))
        try { vm3.VeloRuntime(registry).run(program) } finally { System.setOut(old) }
        return bytes.toString().trimEnd('\n')
    }
}
