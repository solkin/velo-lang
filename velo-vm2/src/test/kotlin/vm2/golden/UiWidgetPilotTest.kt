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
 * Phase-5 pilot: the velo-android UI pattern, in miniature. Today every widget
 * collapses into one God-`View` native because Velo had no subtyping; with
 * structural interfaces each widget can be its own typed native that shares a
 * `View` interface. These host classes mirror that target shape:
 *
 *  - shared modifiers (`visible`) return `Self`, so a fluent chain keeps the
 *    concrete widget type;
 *  - a `Column` container accepts any `View` and is heterogeneous;
 *  - widget-specific methods (`label` on a button, `text` on a label) live only
 *    on their own type, so calling one through the wrong type is a compile error
 *    — exactly the safety the God-`View` lacks (where it is a silent no-op).
 *
 * This proves the migration is mechanically sound: split [VeloView]-style classes
 * into per-widget natives, declare a Velo `interface View`, and containers take
 * `View`. The remaining velo-android work is the (large, mechanical) split itself.
 */
class UiWidgetPilotTest {

    class Button(private val text: String) {
        private val log = StringBuilder()
        fun label(s: String): Button { log.append("label=$s;"); return this }
        fun visible(on: Boolean): Button { log.append("vis=$on;"); return this }
        fun render(): String = "Button($text)[$log]"
    }

    class Label(private var value: String) {
        fun text(s: String): Label { value = s; return this }
        fun visible(on: Boolean): Label { return this }
        fun render(): String = "Label($value)"
    }

    class Column {
        private val children = ArrayList<Any>()
        fun add(child: Any): Column { children.add(child); return this }
        fun render(): String = children.joinToString(",") {
            when (it) { is Button -> it.render(); is Label -> it.render(); else -> "?" }
        }
    }

    private fun registry() = NativeRegistry()
        .register(Terminal::class)
        .register(Button::class)
        .register(Label::class)
        .register(Column::class)

    private fun compile(source: String): core.SerializedProgram? {
        val f = File.createTempFile("uipilot", ".vel").apply { writeText(source); deleteOnExit() }
        return try { VeloCompiler(registry()).compile(f.path) } catch (e: Throwable) { null } finally { f.delete() }
    }

    private fun run(source: String): String {
        val program = compile(source) ?: error("compile failed")
        val baos = ByteArrayOutputStream(); val old = System.out
        System.setOut(PrintStream(baos))
        try { VeloRuntime(registry()).run(program) } finally { System.setOut(old) }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `a heterogeneous column of typed widgets behind one View interface`() {
        val out = run(
            """
            Terminal term = new Terminal();

            # The shared widget contract — only the truly-common modifiers.
            interface View { func visible(bool on) Self; func render() str; };

            Column col = new Column();
            # Each builder returns its own concrete type, so widget-specific methods
            # stay available through the chain (Self), then it is added as a View.
            col.add(new Button("ok").label("Save").visible(true));
            col.add(new Label("hi").text("Hello").visible(true));

            term.println(col.render());
            """.trimIndent()
        )
        assertEquals("Button(ok)[label=Save;vis=true;],Label(Hello)", out)
    }

    @Test
    fun `calling a button-only method on a label is a compile error`() {
        // `.label(...)` exists on Button, not Label — the God-View made this a
        // silent no-op; typed widgets make it a compile-time rejection.
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                Label l = new Label("x");
                l.label("nope");
                """.trimIndent()
            )
        )
    }

    @Test
    fun `a View-typed value only exposes interface methods`() {
        // Through the `View` interface you can call shared methods, but not a
        // widget-specific one — caught at compile time.
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface View { func visible(bool on) Self; func render() str; };
                View v = new Button("x");
                v.label("nope");
                """.trimIndent()
            )
        )
    }
}
