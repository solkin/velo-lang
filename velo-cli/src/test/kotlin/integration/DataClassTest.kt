package integration

import FileSystem
import Http
import Terminal
import Time
import core.NativeRegistry
import core.SerializedFrame
import core.SerializedProgram
import compiler.CompilerFrame
import compiler.CompilerShared
import compiler.Context
import compiler.parser.InputStack
import compiler.parser.Parser
import compiler.parser.SimpleInput
import compiler.parser.StringInput
import compiler.parser.TokenStream
import vm.VM
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

/**
 * Coverage of the `data class` value type. At the bytecode level a data class
 * is an ordinary class, so construction, field reads and methods run through
 * the normal path here; the value-type contract (immutable fields,
 * methods-only body, transferable fields) is enforced at compile time.
 *
 * Cross-actor / native transfer of data classes is covered separately once
 * the runtime marshalling lands.
 */
class DataClassTest {

    @Test
    fun `data class constructs, reads fields and runs methods`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            data class Point(int x, int y) {
                func sum() int { x + y; };
            };

            Point p = new Point(3, 4);
            term.println(p.x.str);
            term.println(p.y.str);
            term.println(p.sum().str);
            """.trimIndent()
        )
        assertEquals("3\n4\n7", output)
    }

    @Test
    fun `reassigning a data class field is a compile error`() {
        assertFails {
            compileOrThrow(
                """
                data class Point(int x, int y) {
                    func mutate() int { x = 99; x; };
                };
                Point p = new Point(1, 2);
                """.trimIndent()
            )
        }
    }

    @Test
    fun `a field declared in the body is a compile error`() {
        assertFails {
            compileOrThrow(
                """
                data class Bad(int x) {
                    int y = 10;
                };
                """.trimIndent()
            )
        }
    }

    @Test
    fun `a non-transferable field is a compile error`() {
        assertFails {
            compileOrThrow(
                """
                class Plain(int a) {};
                data class Bad(Plain p) {};
                """.trimIndent()
            )
        }
    }

    @Test
    fun `a generic data class is a compile error`() {
        assertFails {
            compileOrThrow(
                """
                data class Box[T](T value) {};
                """.trimIndent()
            )
        }
    }

    @Test
    fun `a data class is transferable in actor signatures`() {
        // Compiles because data classes are transferable; the actual cross-thread
        // marshalling is exercised by the runtime tests.
        val program = compileProgram(
            """
            data class Point(int x, int y) {};

            actor class Maker() {
                func make() Point { new Point(1, 2); };
            };
            """.trimIndent()
        )
        assertNotNull(program, "data class should be accepted in an actor signature")
    }

    private val testRegistry = NativeRegistry()
        .register(Terminal::class)
        .register(Time::class)
        .register(FileSystem::class)
        .register(Http::class)

    private fun compileProgram(src: String): SerializedProgram? {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val parser = Parser(stream, SimpleInput(emptyMap(), input), nativeRegistry = testRegistry)
        val node = parser.parse()
        val shared = CompilerShared(testRegistry)
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(0, mutableListOf(), mutableMapOf(), AtomicInteger()),
            frameCounter = AtomicInteger(),
            shared = shared,
        )
        return try {
            node.compile(ctx)
            SerializedProgram(
                natives = shared.nativePool.toList(),
                frames = ctx.frames().map {
                    SerializedFrame(num = it.num, ops = it.ops, vars = it.vars.map { v -> v.value.index })
                },
            )
        } catch (ex: Throwable) {
            ex.printStackTrace()
            null
        }
    }

    private fun compileOrThrow(src: String) {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val parser = Parser(stream, SimpleInput(emptyMap(), input), nativeRegistry = testRegistry)
        val node = parser.parse()
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(0, mutableListOf(), mutableMapOf(), AtomicInteger()),
            frameCounter = AtomicInteger(),
            shared = CompilerShared(testRegistry),
        )
        node.compile(ctx)
    }

    private fun compileAndRun(src: String): String {
        val program = assertNotNull(compileProgram(src), "compilation failed")
        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos))
        try {
            val vm = VM(testRegistry)
            vm.load(program)
            vm.run()
        } finally {
            System.setOut(oldOut)
        }
        return baos.toString().lines()
            .filterNot { line ->
                line.startsWith("Program ended") ||
                    line.startsWith("Program halted") ||
                    line.startsWith("VM stopped") ||
                    line.startsWith("Parsed in") ||
                    line.startsWith("Compiled in") ||
                    line.startsWith("✓ Program") ||
                    line.startsWith("⏹ Program")
            }
            .joinToString("\n")
            .trimEnd('\n')
    }
}
