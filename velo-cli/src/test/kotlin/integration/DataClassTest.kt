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

    @Test
    fun `a data class round-trips through an actor and keeps its methods`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            data class Point(int x, int y) {
                func sum() int { x + y; };
            };

            actor class Echo() {
                func bounce(Point p) Point { p; };
            };

            actor[Echo] e = new Echo();
            Point r = await async e.bounce(new Point(3, 4));
            term.println(r.x.str);
            term.println(r.y.str);
            term.println(r.sum().str);
            """.trimIndent()
        )
        assertEquals("3\n4\n7", output)
    }

    @Test
    fun `an actor can build and return a data class`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            data class Pair(int a, int b) {};

            actor class Maker() {
                func make(int a, int b) Pair { new Pair(a, b); };
            };

            actor[Maker] m = new Maker();
            Pair p = await async m.make(10, 20);
            term.println(p.a.str);
            term.println(p.b.str);
            """.trimIndent()
        )
        assertEquals("10\n20", output)
    }

    @Test
    fun `nested data classes transfer recursively`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            data class Inner(int v) {};
            data class Outer(Inner inner, int k) {};

            actor class Echo() {
                func bounce(Outer o) Outer { o; };
            };

            actor[Echo] e = new Echo();
            Outer o = await async e.bounce(new Outer(new Inner(7), 9));
            term.println(o.inner.v.str);
            term.println(o.k.str);
            """.trimIndent()
        )
        assertEquals("7\n9", output)
    }

    @Test
    fun `data classes compare by value`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            data class Point(int x, int y) {};

            Point a = new Point(1, 2);
            Point b = new Point(1, 2);
            Point c = new Point(1, 9);
            term.println(if (a == b) then "eq" else "ne");
            term.println(if (a == c) then "eq" else "ne");
            """.trimIndent()
        )
        assertEquals("eq\nne", output)
    }

    @Test
    fun `value equality recurses through array fields`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            data class Vec(array[int] xs) {};

            Vec a = new Vec(new array[int]{1, 2, 3});
            Vec b = new Vec(new array[int]{1, 2, 3});
            Vec c = new Vec(new array[int]{1, 2, 4});
            term.println(if (a == b) then "eq" else "ne");
            term.println(if (a == c) then "eq" else "ne");
            """.trimIndent()
        )
        assertEquals("eq\nne", output)
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
                    SerializedFrame(num = it.num, ops = it.ops, vars = (it.varBase until it.varCounter.get()).toList())
                },
                dataClasses = shared.dataClasses.toList(),
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
