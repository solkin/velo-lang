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
 * Numeric widening/narrowing rules: byte -> int -> float widens implicitly (and
 * keeps float arithmetic semantics at runtime), while lossy narrowing must be
 * written explicitly with `.int()` / `.byte()`. Negative cases assert the
 * compiler rejects the program (returns null).
 */
class NumericCoercionTest {

    private fun compile(source: String): core.SerializedProgram? {
        val registry = NativeRegistry().register(Terminal::class)
        val velFile = File.createTempFile("num", ".vel").apply { writeText(source); deleteOnExit() }
        return try {
            VeloCompiler(registry).compile(velFile.path)
        } catch (e: Throwable) {
            null
        } finally {
            velFile.delete()
        }
    }

    private fun run(source: String): String {
        val registry = NativeRegistry().register(Terminal::class)
        val velFile = File.createTempFile("num", ".vel").apply { writeText(source); deleteOnExit() }
        val program = VeloCompiler(registry).compile(velFile.path) ?: error("compile failed")
        velFile.delete()
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            VeloRuntime(registry).run(program)
        } finally {
            System.setOut(old)
        }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `widening an int into a float slot keeps float division`() {
        // The classic trap: `float f = 5` must store a real float so `f / 2` is
        // float division (2.5), not integer division (2).
        assertEquals(
            "2.5\n3.5",
            run(
                """
                Terminal term = new Terminal();
                float f = 5;
                term.println((f / 2).str());
                int n = 7;
                float q = n;
                term.println((q / 2).str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `a float parameter accepts a narrower int argument`() {
        assertEquals(
            "4.5",
            run(
                """
                Terminal term = new Terminal();
                func scale(float x) float { return x / 2; };
                term.println(scale(9).str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `explicit conversions truncate and wrap`() {
        assertEquals(
            "3\n66",
            run(
                """
                Terminal term = new Terminal();
                float pi = 3.75;
                term.println(pi.int().str());
                int code = 322;
                term.println(code.byte().str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `assigning a float to an int without conversion is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                float f = 3.5;
                int i = f;
                """.trimIndent()
            )
        )
    }

    @Test
    fun `a float literal cannot initialize an int`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                int i = 3.0;
                """.trimIndent()
            )
        )
    }

    @Test
    fun `assigning an int to a byte without conversion is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                int n = 5;
                byte b = n;
                """.trimIndent()
            )
        )
    }

    @Test
    fun `an int literal out of byte range is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                byte b = 300;
                """.trimIndent()
            )
        )
    }

    @Test
    fun `returning a float where an int is declared is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                func f(float a) int { return a * 100; };
                term.println(f(1.0).str());
                """.trimIndent()
            )
        )
    }
}
