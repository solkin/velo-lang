package integration

import core.NativeMappingException
import core.NativeRegistry
import core.VmType
import vm.VM
import vm.VMContext
import vm.VMExecutor
import vm.VMProfiler
import vm.VeloProgram
import vm.VeloRuntime

import Terminal
import compiler.CompilerFrame
import compiler.CompilerShared
import compiler.Context
import compiler.parser.InputStack
import compiler.parser.Parser
import compiler.parser.SimpleInput
import compiler.parser.StringInput
import compiler.parser.TokenStream
import core.SerializedFrame
import core.SerializedProgram
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The registration-based native binding pipeline: descriptor introspection
 * rules, compile-time synthesis from the registry, and load-time linking of
 * the program's native pool.
 */
class NativeBindingTest {

    private val registry = NativeRegistry()
        .register(Terminal::class)
        .register(Box::class)
        .register(KotlinCallbacks::class)

    // ---- introspection rules ----

    @Test
    fun `overloaded host methods are rejected with a clear error`() {
        val r = NativeRegistry().register(OverloadedHost::class)
        val ex = assertFailsWith<NativeMappingException> { r.descriptor("OverloadedHost") }
        assertTrue(ex.message!!.contains("overloaded"), "Unexpected: ${ex.message}")
        assertTrue(ex.message!!.contains("'f'"), "Unexpected: ${ex.message}")
    }

    @Test
    fun `multiple public constructors are rejected`() {
        val r = NativeRegistry().register(TwoCtorsHost::class)
        val ex = assertFailsWith<NativeMappingException> { r.descriptor("TwoCtorsHost") }
        assertTrue(ex.message!!.contains("exactly one public"), "Unexpected: ${ex.message}")
    }

    @Test
    fun `descriptor maps kotlin function type to a full velo signature`() {
        val descriptor = assertNotNull(registry.descriptor("KotlinCallbacks"))
        val each = assertNotNull(descriptor.methods["each"])
        assertEquals(listOf<VmType>(VmType.Func(args = listOf(VmType.Int), ret = VmType.Void)), each.params)
    }

    // ---- end-to-end over synthesized types ----

    @Test
    fun `constructor args and native values flow between host methods`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();
            Box a = new Box("hello");
            Box b = a.wrap("say-");
            term.println(b.read());
            a.fill(b);
            term.println(a.read());
            """.trimIndent()
        )
        assertEquals("say-hello\nsay-hello", output)
    }

    @Test
    fun `kotlin function type parameter receives a velo callback`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();
            KotlinCallbacks k = new KotlinCallbacks();
            int sum = 0;
            k.each(func(int v) void {
                sum = sum + v;
                term.println(sum.str);
                void
            });
            term.println("posted");
            """.trimIndent()
        )
        // each() posts three invocations through the main mailbox; they run
        // after the main frame, in order.
        assertEquals("posted\n1\n3\n6", output)
    }

    @Test
    fun `kotlin function type parameter is checked at compile time`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            compileOrThrow(
                """
                KotlinCallbacks k = new KotlinCallbacks();
                k.each(func(str s) void { void });
                """.trimIndent()
            )
        }
        assertTrue(ex.message!!.contains("each"), "Unexpected: ${ex.message}")
    }

    @Test
    fun `method argument types are checked at compile time`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            compileOrThrow(
                """
                Box a = new Box("x");
                a.wrap(42);
                """.trimIndent()
            )
        }
        assertTrue(ex.message!!.contains("wrap"), "Unexpected: ${ex.message}")
    }

    @Test
    fun `unknown method on a native class fails at compile time`() {
        val ex = assertFailsWith<Throwable> {
            compileOrThrow(
                """
                Box a = new Box("x");
                a.explode();
                """.trimIndent()
            )
        }
        assertTrue(ex.message!!.contains("explode"), "Unexpected: ${ex.message}")
    }

    // ---- linking ----

    @Test
    fun `loading against a runtime without the classes lists every missing entry`() {
        val program = assertNotNull(
            compile(
                """
                Terminal term = new Terminal();
                Box a = new Box("x");
                term.println(a.read());
                """.trimIndent()
            )
        )
        val vm = VM(NativeRegistry()) // nothing registered
        val ex = assertFailsWith<NativeMappingException> { vm.load(program) }
        val message = ex.message!!
        assertTrue(message.contains("'Terminal'"), "Unexpected: $message")
        assertTrue(message.contains("'Box'"), "Unexpected: $message")
    }

    @Test
    fun `pool deduplicates repeated references to the same entry`() {
        val program = assertNotNull(
            compile(
                """
                Terminal term = new Terminal();
                term.println("a");
                term.println("b");
                term.println("c");
                """.trimIndent()
            )
        )
        // One Terminal constructor + one println — repeated calls share the entry.
        assertEquals(2, program.natives.size)
    }

    // ---- helpers ----

    private fun compile(src: String): SerializedProgram? {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val parser = Parser(stream, SimpleInput(emptyMap(), input), nativeRegistry = registry)
        val node = parser.parse()
        val shared = CompilerShared(registry)
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
                    SerializedFrame(
                        num = it.num,
                        ops = it.ops,
                        vars = it.vars.map { v -> v.value.index },
                    )
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
        val parser = Parser(stream, SimpleInput(emptyMap(), input), nativeRegistry = registry)
        val node = parser.parse()
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(0, mutableListOf(), mutableMapOf(), AtomicInteger()),
            frameCounter = AtomicInteger(),
            shared = CompilerShared(registry),
        )
        node.compile(ctx)
    }

    private fun compileAndRun(src: String): String {
        val program = assertNotNull(compile(src), "compilation failed")
        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos))
        try {
            val vm = VM(registry)
            vm.load(program)
            vm.run()
        } finally {
            System.setOut(oldOut)
        }
        return baos.toString().lines()
            .filterNot { it.startsWith("✓ Program") || it.startsWith("⏹ Program") }
            .joinToString("\n")
            .trimEnd('\n')
    }
}
