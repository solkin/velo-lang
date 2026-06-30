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
import kotlin.test.assertNotNull

/**
 * End-to-end coverage for structural interfaces, `Self`, and the dynamic
 * `Op.MethodLoad` dispatch path: compile Velo source against a `Terminal` and
 * run it, asserting on captured stdout. Negative cases assert the compiler
 * rejects the program (returns null).
 */
class InterfaceDispatchTest {

    private fun compile(source: String): core.SerializedProgram? {
        val registry = NativeRegistry().register(Terminal::class)
        val velFile = File.createTempFile("iface", ".vel").apply { writeText(source); deleteOnExit() }
        return try {
            // VeloCompiler swallows compile-phase errors (returns null) but lets parse-phase
            // errors propagate; for a rejection test either way means "did not compile".
            VeloCompiler(registry).compile(velFile.path)
        } catch (e: Throwable) {
            null
        } finally {
            velFile.delete()
        }
    }

    private fun run(source: String): String {
        val registry = NativeRegistry().register(Terminal::class)
        val velFile = File.createTempFile("iface", ".vel").apply { writeText(source); deleteOnExit() }
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
    fun `interface-typed receiver dispatches to the concrete class`() {
        val out = run(
            """
            Terminal term = new Terminal();

            interface Shape {
                func area() int;
                func kind() str;
            };

            class Square(int side) {
                func area() int { return side * side; };
                func kind() str { return "square"; };
            };

            class Rect(int w, int h) {
                func area() int { return w * h; };
                func kind() str { return "rect"; };
            };

            Shape s = new Square(4);
            term.println(s.kind());
            term.println(s.area().str());
            s = new Rect(3, 5);
            term.println(s.kind());
            term.println(s.area().str());
            """.trimIndent()
        )
        assertEquals(listOf("square", "16", "rect", "15"), out.lines())
    }

    @Test
    fun `interface value passes through a function parameter`() {
        val out = run(
            """
            Terminal term = new Terminal();

            interface Shape { func area() int; };
            class Square(int side) { func area() int { return side * side; }; };
            class Rect(int w, int h) { func area() int { return w * h; }; };

            func total(Shape a, Shape b) int { return a.area() + b.area(); };

            term.println(total(new Square(2), new Rect(3, 4)).str());
            """.trimIndent()
        )
        assertEquals("16", out)
    }

    @Test
    fun `array of interface dispatches per element`() {
        val out = run(
            """
            Terminal term = new Terminal();

            interface Shape { func area() int; };
            class Square(int side) { func area() int { return side * side; }; };
            class Rect(int w, int h) { func area() int { return w * h; }; };

            array[Shape] shapes = new array[Shape]{ new Square(2), new Rect(3, 4), new Square(5) };
            int sum = 0;
            int i = 0;
            while (i < shapes.len()) {
                sum += shapes[i].area();
                i += 1;
            };
            term.println(sum.str());
            """.trimIndent()
        )
        assertEquals("41", out) // 4 + 12 + 25
    }

    @Test
    fun `Self return resolves to the concrete type for chaining`() {
        val out = run(
            """
            Terminal term = new Terminal();

            class Counter(int n) {
                func grow() Self { return new Counter(n + 1); };
                func value() int { return n; };
            };

            Counter c = new Counter(0);
            term.println(c.grow().grow().grow().value().str());
            """.trimIndent()
        )
        assertEquals("3", out)
    }

    @Test
    fun `Self return through an interface keeps dispatching`() {
        val out = run(
            """
            Terminal term = new Terminal();

            interface Builder {
                func grow() Self;
                func value() int;
            };

            class Counter(int n) {
                func grow() Self { return new Counter(n + 1); };
                func value() int { return n; };
            };

            Builder b = new Counter(10);
            term.println(b.grow().grow().value().str());
            """.trimIndent()
        )
        assertEquals("12", out)
    }

    @Test
    fun `explicit conformance compiles when satisfied`() {
        assertNotNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; };
                class Square(int side) : Shape { func area() int { return side * side; }; };
                Square s = new Square(3);
                term.println(s.area().str());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `explicit conformance is rejected when a method is missing`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; func kind() str; };
                class Square(int side) : Shape { func area() int { return side * side; }; };
                """.trimIndent()
            )
        )
    }

    @Test
    fun `assigning a class missing a method to an interface is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; };
                class Dot() { func draw() int { return 0; }; };
                Shape s = new Dot();
                """.trimIndent()
            )
        )
    }

    @Test
    fun `calling a method outside the interface is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; };
                class Square(int side) { func area() int { return side * side; }; func kind() str { return "sq"; }; };
                Shape s = new Square(2);
                term.println(s.kind());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `interface is not transferable across an actor boundary`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; };
                actor class Worker() {
                    func handle(Shape s) int { return s.area(); };
                };
                """.trimIndent()
            )
        )
    }

    @Test
    fun `bounded generic calls interface methods on the type parameter`() {
        val out = run(
            """
            Terminal term = new Terminal();

            interface Shape { func area() int; };
            class Square(int side) { func area() int { return side * side; }; };
            class Rect(int w, int h) { func area() int { return w * h; }; };

            class Boxed[T: Shape](T item) {
                func areaOf() int { return item.area(); };
            };

            Boxed[Square] a = new Boxed[Square](new Square(5));
            Boxed[Rect] b = new Boxed[Rect](new Rect(3, 4));
            term.println(a.areaOf().str());
            term.println(b.areaOf().str());
            """.trimIndent()
        )
        assertEquals(listOf("25", "12"), out.lines())
    }

    @Test
    fun `type argument violating a bound is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; };
                class Dot() { func draw() int { return 0; }; };
                class Boxed[T: Shape](T item) { func areaOf() int { return item.area(); }; };
                Boxed[Dot] b = new Boxed[Dot](new Dot());
                """.trimIndent()
            )
        )
    }

    @Test
    fun `bound non-interface type is rejected`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                class Square(int side) {};
                class Boxed[T: Square](T item) {};
                """.trimIndent()
            )
        )
    }

    @Test
    fun `signature mismatch breaks structural satisfaction`() {
        assertNull(
            compile(
                """
                Terminal term = new Terminal();
                interface Shape { func area() int; };
                class Square(int side) { func area() str { return "no"; }; };
                Shape s = new Square(2);
                """.trimIndent()
            )
        )
    }
}
