package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the registry enhancement that backs the velo-android God-`View`
 * split: a concrete native widget inherits shared modifiers from a base class,
 * and a modifier returning the base is exposed as returning the **concrete**
 * type — so a fluent chain keeps that type (and satisfies a `Self` interface).
 */
abstract class BaseWidget {
    protected val log = StringBuilder()
    fun padding(dp: Int): BaseWidget { log.append("pad=$dp;"); return this }   // returns base → exposed as concrete
    fun visible(on: Boolean): BaseWidget { log.append("vis=$on;"); return this }
    fun dump(): String = log.toString()
}

class Btn(label: String) : BaseWidget() {
    init { log.append("btn=$label;") }
    fun text(s: String): Btn { log.append("text=$s;"); return this }   // widget-specific
}

class WidgetInheritanceTest {

    private fun registry() = NativeRegistry().register(Terminal::class).register(Btn::class)

    private fun run(source: String): String {
        val f = File.createTempFile("inherit", ".vel").apply { writeText(source); deleteOnExit() }
        val program = VeloCompiler(registry()).compile(f.path) ?: error("compile failed")
        f.delete()
        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos))
        try { VeloRuntime(registry()).run(program) } finally { System.setOut(old) }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `inherited modifier keeps the concrete type through a chain`() {
        // padding/visible are inherited and return the base, but are exposed as
        // returning Btn — so the Btn-specific `text` stays callable mid-chain.
        val out = run(
            """
            Terminal term = new Terminal();
            Btn b = new Btn("ok");
            term.println(b.padding(8).text("hi").visible(true).dump());
            """.trimIndent()
        )
        assertEquals("btn=ok;pad=8;text=hi;vis=true;", out)
    }

    @Test
    fun `inherited modifiers satisfy a Self interface and dispatch dynamically`() {
        val out = run(
            """
            Terminal term = new Terminal();
            interface Styleable { func padding(int dp) Self; func visible(bool on) Self; func dump() str; };
            Styleable s = new Btn("y");
            term.println(s.padding(4).visible(false).dump());
            """.trimIndent()
        )
        assertEquals("btn=y;pad=4;vis=false;", out)
    }
}
