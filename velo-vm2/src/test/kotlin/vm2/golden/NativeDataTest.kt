package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import core.VeloFunction
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

// Host data-class counterparts (a Kotlin data class satisfies the binding for free).
data class NativePoint(val x: Int, val y: Int)
data class NativeSegment(val a: NativePoint, val b: NativePoint)

/** Host API taking and returning data classes by value, incl. nested. */
class Geometry {
    fun translate(p: NativePoint, dx: Int, dy: Int): NativePoint = NativePoint(p.x + dx, p.y + dy)
    fun origin(): NativePoint = NativePoint(0, 0)
    fun describe(p: NativePoint): String = "(${p.x}, ${p.y})"
    fun start(s: NativeSegment): NativePoint = s.a
}

/** Host API delivering and receiving data classes through Velo callbacks. */
class PointBus {
    fun emit(p: NativePoint, cb: VeloFunction) { cb.post(p) }
    fun mapPoint(p: NativePoint, cb: VeloFunction): NativePoint = cb.call(p).get() as NativePoint
}

/** A registered native class returned by value as an opaque handle. */
class Box(label: String) {
    private val content = label
    fun wrap(prefix: String): Box = Box(prefix + content)
    fun read(): String = content
}

/**
 * Native interop completeness (A3): `data class` values marshalled across the
 * boundary by value (arguments, return values, nested, and through callbacks),
 * plus a registered native class returned as an opaque handle.
 */
class NativeDataTest {

    private fun run(source: String): String {
        val registry = NativeRegistry()
            .register(Terminal::class)
            .registerData("Point", NativePoint::class)
            .registerData("Seg", NativeSegment::class)
            .register(Geometry::class)
            .register(PointBus::class)
            .register(Box::class)
        val velFile = File.createTempFile("nativedata", ".vel").apply { writeText(source); deleteOnExit() }
        val program = VeloCompiler(registry).compile(velFile.path) ?: error("compile failed")
        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos))
        try { VeloRuntime(registry).run(program) } finally { System.setOut(old) }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `data classes marshal across the native boundary`() {
        val out = run(
            """
            Terminal term = new Terminal();
            data class Point(int x, int y) { func sum() int { return x + y; }; };
            data class Seg(Point a, Point b) {};

            Geometry geo = new Geometry();
            Point moved = geo.translate(new Point(10, 20), 5, 6);
            term.println(moved.x.str());
            term.println(moved.y.str());
            term.println(moved.sum().str());
            term.println(geo.describe(new Point(3, 4)));

            Seg s = new Seg(new Point(1, 2), new Point(3, 4));
            Point st = geo.start(s);
            term.println(st.x.str().con(",").con(st.y.str()));

            PointBus bus = new PointBus();
            bus.emit(new Point(7, 8), func(Point p) void { term.println("emit ".con(p.x.str())); void });
            Point mapped = bus.mapPoint(new Point(1, 1), func(Point p) Point { return new Point(p.x + 1, p.y + 1); });
            term.println(mapped.x.str().con(",").con(mapped.y.str()));

            Box b = new Box("hi");
            Box w = b.wrap(">> ");
            term.println(w.read());
            """.trimIndent()
        )
        assertEquals(
            listOf("15", "26", "41", "(3, 4)", "1,2", "emit 7", "2,2", ">> hi"),
            out.lines(),
        )
    }
}
