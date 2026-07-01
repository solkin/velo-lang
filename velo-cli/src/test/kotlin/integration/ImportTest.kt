package integration

import compiler.VeloCompiler
import core.NativeRegistry
import Terminal
import vm.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The module/import system: imports resolve relative to the importing file
 * (across sub-directories), transitively, and a module reached more than one
 * way is loaded only once (dedup by canonical path — no redefinition).
 */
class ImportTest {

    private fun run(root: File): String {
        val natives = NativeRegistry().register(Terminal::class)
        val program = VeloCompiler(natives).compile(root.path) ?: error("compilation failed")
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            VeloRuntime(natives).run(program)
        } finally {
            System.setOut(old)
        }
        return baos.toString().lines().filter { it.isNotBlank() && !it.startsWith("✓") }.joinToString("\n")
    }

    @Test
    fun `imports resolve relative to the importing file, transitively, and dedup`() {
        val dir = File.createTempFile("imp", "").let { it.delete(); it.mkdirs(); it }
        File(dir, "util").mkdirs()
        File(dir, "util/mathx.vel").writeText("func square(int n) int { return n * n }\n")
        // helpers imports a sibling with a relative path.
        File(dir, "util/helpers.vel").writeText(
            "import \"./mathx\"\nfunc cube(int n) int { return square(n) * n }\n"
        )
        // The root imports helpers (which pulls in mathx transitively) AND mathx
        // directly — mathx must load once, not twice (else 'square' redefines).
        File(dir, "main.vel").writeText(
            """
            Terminal term = new Terminal()
            import "util/helpers"
            import "util/mathx"
            import "std/bool"
            term.println(square(4).str())
            term.println(cube(3).str())
            term.println((true & false).str())
            """.trimIndent()
        )

        assertEquals("16\n27\nfalse", run(File(dir, "main.vel")))
        dir.deleteRecursively()
    }

    @Test
    fun `a name clash across modules is a clear error, not a silent override`() {
        val dir = File.createTempFile("imp", "").let { it.delete(); it.mkdirs(); it }
        File(dir, "a.vel").writeText("class Widget(int x) { func get() int { return x } }\n")
        File(dir, "b.vel").writeText("class Widget(int y) { func get() int { return y } }\n")
        File(dir, "main.vel").writeText(
            "Terminal term = new Terminal()\nimport \"a\"\nimport \"b\"\n"
        )

        val natives = NativeRegistry().register(Terminal::class)
        val err = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(err))
        try {
            VeloCompiler(natives).compile(File(dir, "main.vel").path)
        } finally {
            System.setOut(old)
        }
        assertTrue(
            err.toString().contains("Widget") && err.toString().contains("already defined"),
            "expected a clear class-collision error, got: $err",
        )
        dir.deleteRecursively()
    }
}
