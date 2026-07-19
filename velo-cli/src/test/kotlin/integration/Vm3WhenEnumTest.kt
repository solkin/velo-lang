package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `when` / `enum` coverage on the shipping Android backend (vm3), where the new
 * `Op.ClassId` discriminant executes. Mirrors the language-neutral corpus case
 * `conformance/cases/control/when-enum.vel`, pinned to vm3 specifically.
 */
class Vm3WhenEnumTest {

    @Test
    fun `enum match binds fields and recurses`() {
        assertEquals(
            "2",
            run(
                """
                Terminal term = new Terminal()
                enum Expr {
                    Lit(int n)
                    Add(Expr l, Expr r)
                    Neg(Expr e)
                }
                func eval(Expr e) int {
                    return when e {
                        Lit(n)    -> n
                        Add(l, r) -> eval(l) + eval(r)
                        Neg(x)    -> 0 - eval(x)
                    }
                }
                Expr program = new Add(new Lit(3), new Neg(new Lit(1)))
                term.println(eval(program).str())
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `nullary variants dispatch`() {
        assertEquals(
            "#0f0",
            run(
                """
                Terminal term = new Terminal()
                enum Color { Red, Green, Blue }
                func hex(Color c) str {
                    return when c {
                        Red   -> "#f00"
                        Green -> "#0f0"
                        Blue  -> "#00f"
                    }
                }
                term.println(hex(new Green()))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `int switch with else`() {
        assertEquals(
            "Sat",
            run(
                """
                Terminal term = new Terminal()
                int day = 6
                str label = when day {
                    0 -> "Sun"
                    6 -> "Sat"
                    else -> "other"
                }
                term.println(label)
                """.trimIndent(),
            ),
        )
    }

    private fun run(source: String): String {
        val registry = NativeRegistry().register(Terminal::class)
        val file = File.createTempFile("vm3-when", ".vel").apply { writeText(source) }
        val program = try {
            VeloCompiler(registry).compile(file.path) ?: error("compile failed")
        } finally {
            file.delete()
        }
        val bytes = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(bytes))
        try { vm3.VeloRuntime(registry).run(program) } finally { System.setOut(old) }
        return bytes.toString().trimEnd('\n')
    }
}
