package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A registered host class — a stand-in for a native UI widget. It satisfies the
 * Velo `Shape`/`Builder` interfaces structurally (no implements, no annotations),
 * and its fluent [padding] returns itself, fulfilling a `Self`-returning method.
 */
class Widget(private val label: String) {
    private var pad = 0
    fun area(): Int = label.length
    fun kind(): String = "widget:$label"
    fun padding(dp: Int): Widget { pad = dp; return this }
    fun pad(): Int = pad
}

/** End-to-end coverage for a native handle dispatched through a Velo interface. */
class NativeInterfaceTest {

    private fun registry() = NativeRegistry().register(Terminal::class).register(Widget::class)

    private fun compile(source: String): core.SerializedProgram? {
        val f = File.createTempFile("nat", ".vel").apply { writeText(source); deleteOnExit() }
        return try { VeloCompiler(registry()).compile(f.path) } catch (e: Throwable) { null } finally { f.delete() }
    }

    private fun run(source: String): String {
        val program = compile(source) ?: error("compile failed")
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try { VeloRuntime(registry()).run(program) } finally { System.setOut(old) }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `native handle satisfies and dispatches through a Velo interface`() {
        val out = run(
            """
            Terminal term = new Terminal();
            interface Shape { func area() int; func kind() str; };

            Widget w = new Widget("hello");
            Shape s = w;
            term.println(s.kind());
            term.println(s.area().str);
            """.trimIndent()
        )
        assertEquals(listOf("widget:hello", "5"), out.lines())
    }

    @Test
    fun `Self-returning native method keeps dispatching through the interface`() {
        val out = run(
            """
            Terminal term = new Terminal();
            interface Builder { func padding(int dp) Self; func pad() int; };

            Builder b = new Widget("x");
            term.println(b.padding(8).pad().str);
            """.trimIndent()
        )
        assertEquals("8", out)
    }

    @Test
    fun `mixed Velo and native values behind one interface`() {
        val out = run(
            """
            Terminal term = new Terminal();
            interface Shape { func area() int; func kind() str; };
            class Square(int side) { func area() int { side * side; }; func kind() str { "square"; }; };

            func describe(Shape s) str { s.kind().con("=").con(s.area().str); };

            term.println(describe(new Square(3)));
            term.println(describe(new Widget("ab")));
            """.trimIndent()
        )
        assertEquals(listOf("square=9", "widget:ab=2"), out.lines())
    }

    @Test
    fun `native class missing an interface method is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Drawable { func area() int; func draw() str; };
                Drawable d = new Widget("x");
                """.trimIndent()
            )
        )
    }
}
