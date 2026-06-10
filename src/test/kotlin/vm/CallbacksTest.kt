package vm

import Terminal
import VeloProgram
import VeloRuntime
import compiler.CompilerFrame
import compiler.Context
import compiler.parser.InputStack
import compiler.parser.Parser
import compiler.parser.SimpleInput
import compiler.parser.StringInput
import compiler.parser.TokenStream
import utils.BytecodeInputStream
import utils.BytecodeOutputStream
import utils.SerializedFrame
import vm.actors.ThreadDispatcher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * End-to-end coverage of two-way native integration: transferable
 * `func[(args) void]` values, [vm.actors.CallbackRecord] routing between
 * actors, [vm.actors.VeloFunction] on the host side, and the main-context
 * pump that executes callbacks on the program's main thread.
 */
class CallbacksTest {

    @AfterTest
    fun tearDown() {
        TestBridge.reset()
    }

    // ---- Velo-side: callbacks between main and actors ----

    @Test
    fun `callback handed to actor runs on main after the main frame`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Worker() {
                func process(int value, func[(int) void] done) void {
                    done(value * 2);
                    void
                };
            };

            actor[Worker] w = new Worker();
            await async w.process(21, func(int v) void {
                term.println("callback got: ".con(v.str));
                void
            });
            term.println("main frame done");
            """.trimIndent()
        )
        // The callback is only *posted* during the actor call; the main
        // context executes it after its own frame completes.
        assertEquals("main frame done\ncallback got: 42", output)
    }

    @Test
    fun `callback captures and mutates main context state`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Worker() {
                func run(func[(int) void] cb) void {
                    cb(5);
                    cb(7);
                    void
                };
            };

            int sum = 0;
            actor[Worker] w = new Worker();
            await async w.run(func(int v) void {
                sum = sum + v;
                term.println("sum: ".con(sum.str));
                void
            });
            term.println("done");
            """.trimIndent()
        )
        // Both invocations execute serially on main, in posting order.
        assertEquals("done\nsum: 5\nsum: 12", output)
    }

    @Test
    fun `actor-to-actor callback executes on the owning actor`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Holder() {
                int n = 0;
                func makeAdder() func[(int) void] {
                    func(int v) void {
                        n = n + v;
                        void
                    };
                };
                func value() int { n; };
            };

            actor class Firer() {
                func fire(func[(int) void] cb, int v) void {
                    cb(v);
                    void
                };
            };

            actor[Holder] a = new Holder();
            actor[Firer] b = new Firer();

            func[(int) void] cb = await async a.makeAdder();
            await async b.fire(cb, 5);
            await async b.fire(cb, 9);
            term.println("a.n = ".con((await async a.value()).str));
            """.trimIndent()
        )
        // b posts InvokeFunc into a's mailbox before fire() returns, so the
        // later value() call is serialised after both increments.
        assertEquals("a.n = 14", output)
    }

    @Test
    fun `callback coming home unwraps to a local function`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Echo() {
                func give(func[(int) void] cb) func[(int) void] { cb; };
            };

            actor[Echo] e = new Echo();
            func[(int) void] back = await async e.give(func(int v) void {
                term.println("local: ".con(v.str));
                void
            });
            back(7);
            term.println("after");
            """.trimIndent()
        )
        // The round-tripped value is the original closure again, so the call
        // is an ordinary local invocation — it prints *before* "after".
        assertEquals("local: 7\nafter", output)
    }

    // ---- compile-time transferability rules ----

    @Test
    fun `actor signature rejects callback returning a value`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() {
                    func m(func[(int) int] cb) void { void };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
        assertTrue(msg.contains("return void"), "Hint missing: $msg")
    }

    @Test
    fun `actor signature rejects loose func type without signature`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() {
                    func m(func[void] cb) void { void };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `actor signature rejects callback with non-transferable argument`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() {
                    func m(func[(ptr[int]) void] cb) void { void };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `full func signature is enforced on assignment`() {
        val ex = assertFails {
            compileOrThrow(
                """
                func[(int) void] cb = func(str s) void { void };
                """.trimIndent()
            )
        }
        assertNotNull(ex.message)
    }

    @Test
    fun `full func signature accepts a matching lambda and call`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            func[(int, str) void] show = func(int n, str s) void {
                term.println(s.con(": ").con(n.str));
                void
            };
            show(3, "n");
            """.trimIndent()
        )
        assertEquals("n: 3", output)
    }

    // ---- host side: VeloFunction via native classes ----

    @Test
    fun `native callback fired from a background thread executes on the main thread`() {
        val mainThread = Thread.currentThread().name
        val output = compileAndRun(
            """
            include "lang/terminal.vel";
            $bridgeLib

            bridge.register(func(int v) void {
                bridge.mark();
                term.println("native got: ".con(v.str));
                bridge.release();
                void
            });
            bridge.fireFromBackground(21);
            term.println("fired");
            """.trimIndent()
        )
        assertEquals("fired\nnative got: 21", output)
        // vm.run pumps on the calling (JUnit) thread — the callback body must
        // have executed exactly there, not on the firing thread.
        assertEquals(mainThread, TestBridge.invokeThread)
    }

    @Test
    fun `native callback invoked inline from the owner thread`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";
            $bridgeLib

            bridge.register(func(int v) void {
                term.println("inline: ".con(v.str));
                void
            });
            bridge.fireInline(5);
            term.println("after inline");
            bridge.release();
            """.trimIndent()
        )
        // call() from the owner's own thread executes the body immediately —
        // before fireInline returns — instead of deadlocking on the mailbox.
        assertEquals("inline: 5\nafter inline", output)
    }

    @Test
    fun `embedded program on a host dispatcher runs callbacks on that dispatcher`() {
        val src = """
            include "lang/terminal.vel";
            $bridgeLib

            bridge.register(func(int v) void {
                bridge.mark();
                term.println("embedded got: ".con(v.str));
                void
            });
            term.println("embedded ready");
        """.trimIndent()
        val frames = assertNotNull(compile(src), "compilation failed")
        val runtime = VeloRuntime().register("TestBridge", TestBridge::class)

        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos, true))
        var program: VeloProgram? = null
        try {
            program = runtime.start(frames, ThreadDispatcher("test-main-dispatcher"))
            awaitNotNull("callback registration") { TestBridge.captured }
            val cb = assertNotNull(TestBridge.captured)

            // Invoke from the JUnit thread; body must run on the dispatcher.
            cb.call(7).get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals("test-main-dispatcher", TestBridge.invokeThread)

            // Host-side argument validation fails fast on the caller.
            assertFails { cb.post("not an int") }
            assertFails { cb.post() }
            assertFails { cb.post(1, 2) }
        } finally {
            System.setOut(oldOut)
            program?.stop()
        }
        val output = extractProgramOutput(baos.toString())
        assertEquals("embedded ready\nembedded got: 7", output)
    }

    // ---- bytecode roundtrip ----

    @Test
    fun `func signature survives a vbc write-read roundtrip`() {
        val src = """
            include "lang/terminal.vel";

            actor class Worker() {
                func process(int value, func[(int) void] done) void {
                    done(value + 1);
                    void
                };
            };

            actor[Worker] w = new Worker();
            await async w.process(41, func(int v) void {
                term.println("roundtrip: ".con(v.str));
                void
            });
            term.println("main done");
        """.trimIndent()
        val frames = assertNotNull(compile(src), "compilation failed")

        val bytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                BytecodeOutputStream(dos).use { bos ->
                    bos.write(frames)
                    bos.flush()
                }
            }
            baos.toByteArray()
        }
        val reread = DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            BytecodeInputStream(dis).readFrames()
        }

        assertEquals(frames.size, reread.size)
        val output = runFrames(reread)
        assertEquals("main done\nroundtrip: 42", output)
    }

    // ---- helpers ----

    private val terminalLib = """
        native class Terminal() {
            native func print(str text) str;
            native func input() str;
            func println(str text) str {
                print(text.con("\n"));
            };
        };

        Terminal term = new Terminal();
    """.trimIndent()

    private val bridgeLib = """
        native class TestBridge() {
            native func register(func[(int) void] cb) void;
            native func mark() void;
            native func fireFromBackground(int value) void;
            native func fireInline(int value) void;
            native func release() void;
        };
        TestBridge bridge = new TestBridge();
    """.trimIndent()

    private fun compile(src: String): List<SerializedFrame>? {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val deps = mapOf("lang/terminal.vel" to terminalLib)
        val parser = Parser(stream, SimpleInput(deps, input))
        val node = parser.parse()
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(0, mutableListOf(), mutableMapOf(), AtomicInteger()),
            frameCounter = AtomicInteger(),
        )
        return try {
            node.compile(ctx)
            ctx.frames().map {
                SerializedFrame(
                    num = it.num,
                    ops = it.ops,
                    vars = it.vars.map { v -> v.value.index },
                )
            }
        } catch (ex: Throwable) {
            ex.printStackTrace()
            null
        }
    }

    private fun compileOrThrow(src: String) {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val deps = mapOf("lang/terminal.vel" to terminalLib)
        val parser = Parser(stream, SimpleInput(deps, input))
        val node = parser.parse()
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(0, mutableListOf(), mutableMapOf(), AtomicInteger()),
            frameCounter = AtomicInteger(),
        )
        node.compile(ctx)
    }

    private fun compileAndRun(src: String): String {
        val frames = assertNotNull(compile(src), "compilation failed")
        return runFrames(frames)
    }

    private fun runFrames(frames: List<SerializedFrame>): String {
        val nativeRegistry = NativeRegistry()
            .register(Terminal::class)
            .register("TestBridge", TestBridge::class)
        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos))
        try {
            val vm = VM(nativeRegistry)
            vm.load(SimpleParser(frames))
            vm.run()
        } finally {
            System.setOut(oldOut)
        }
        return extractProgramOutput(baos.toString())
    }

    private fun awaitNotNull(what: String, timeoutMs: Long = 5_000, probe: () -> Any?) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (probe() != null) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for $what")
    }

    private fun extractProgramOutput(fullOutput: String): String {
        return fullOutput.lines()
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
