package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import core.SerializedProgram
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/** A host class standing in for a native widget, satisfying interfaces structurally. */
class ParityWidget(private val label: String) {
    private var pad = 0
    fun area(): Int = label.length
    fun kind(): String = "widget:$label"
    fun padding(dp: Int): ParityWidget { pad = dp; return this }
    fun pad(): Int = pad
}

/**
 * Interface dispatch — Velo instances, native handles, and `Self` — must produce
 * identical output on the legacy VM and vm2. This pins the legacy `Op.InterfaceCall`
 * implementation (both its Velo and native branches) to the reference vm2 one.
 */
class InterfaceParityTest {

    private fun registry() = NativeRegistry().register(Terminal::class).register("Widget", ParityWidget::class)

    private fun capture(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try { block() } finally { System.setOut(old) }
        return baos.toString().lineSequence()
            .filterNot { it.startsWith("✓ Program") || it.startsWith("⏹ Program") || it.isBlank() }
            .joinToString("\n")
    }

    @Test
    fun `interface dispatch matches across both VMs`() {
        val source = """
            Terminal term = new Terminal();

            interface Shape { func area() int; func kind() str; };
            interface Builder { func padding(int dp) Self; func pad() int; };

            class Square(int side) { func area() int { return side * side; }; func kind() str { return "square"; }; };

            func describe(Shape s) str { return s.kind().con("=").con(s.area().str()); };

            term.println(describe(new Square(4)));
            term.println(describe(new Widget("abc")));

            array[Shape] shapes = new array[Shape]{ new Square(2), new Widget("zz") };
            int i = 0;
            while (i < shapes.len()) { term.println(shapes[i].area().str()); i += 1; };

            Builder b = new Widget("x");
            term.println(b.padding(7).pad().str());
        """.trimIndent()

        val f = File.createTempFile("ifaceparity", ".vel").apply { writeText(source); deleteOnExit() }
        val program: SerializedProgram = VeloCompiler(registry()).compile(f.path) ?: fail("compile failed")
        f.delete()

        val legacy = capture { vm.VeloRuntime(registry()).run(program) }
        val fresh = capture { vm2.VeloRuntime(registry()).run(program) }
        val compact = capture { vm3.VeloRuntime(registry()).run(program) }

        assertEquals(fresh, legacy, "legacy VM diverged from vm2")
        assertEquals(fresh, compact, "vm3 diverged from vm2")
        assertEquals(listOf("square=16", "widget:abc=3", "4", "2", "7"), fresh.lines())
    }
}
