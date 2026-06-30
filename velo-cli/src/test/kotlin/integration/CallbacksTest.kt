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
import core.Bytecode
import core.SerializedFrame
import core.SerializedProgram
import host.ThreadDispatcher
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
    fun `callback handed to actor runs on main while parked at await`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            actor class Worker() {
                func process(int value, func[(int) void] done) void {
                    done(value * 2);
                    void
                };
            };

            actor[Worker] w = new Worker();
            await async w.process(21, func(int v) void {
                term.println("callback got: ".con(v.str()));
                void
            });
            term.println("main frame done");
            """.trimIndent()
        )
        // `await` is a yield point (VEL-11): while main is parked awaiting
        // the call, it drains its mailbox, so the callback posted during the
        // call runs before main resumes and finishes its frame.
        assertEquals("callback got: 42\nmain frame done", output)
    }

    @Test
    fun `callback captures and mutates main context state`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

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
                term.println("sum: ".con(sum.str()));
                void
            });
            term.println("done");
            """.trimIndent()
        )
        // Both invocations execute serially on main, in posting order, while
        // main is parked at the `await` (a yield point); "done" prints after.
        assertEquals("sum: 5\nsum: 12\ndone", output)
    }

    @Test
    fun `actor-to-actor callback executes on the owning actor`() {
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

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
            term.println("a.n = ".con((await async a.value()).str()));
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
            Terminal term = new Terminal();

            actor class Echo() {
                func give(func[(int) void] cb) func[(int) void] { cb; };
            };

            actor[Echo] e = new Echo();
            func[(int) void] back = await async e.give(func(int v) void {
                term.println("local: ".con(v.str()));
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
    fun `actor signature accepts a value-returning callback`() {
        // A non-void callback is transferable as long as its return type is.
        compileOrThrow(
            """
            actor class A() {
                func m(func[(int) int] cb) void { void };
            };
            """.trimIndent()
        )
    }

    @Test
    fun `actor signature rejects callback with non-transferable return`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() {
                    func m(func[(int) ptr[int]] cb) void { void };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
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
            Terminal term = new Terminal();

            func[(int, str) void] show = func(int n, str s) void {
                term.println(s.con(": ").con(n.str()));
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
            Terminal term = new Terminal();
            $bridgeLib

            bridge.register(func(int v) void {
                bridge.mark();
                term.println("native got: ".con(v.str()));
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
            Terminal term = new Terminal();
            $bridgeLib

            bridge.register(func(int v) void {
                term.println("inline: ".con(v.str()));
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
            Terminal term = new Terminal();
            $bridgeLib

            bridge.register(func(int v) void {
                bridge.mark();
                term.println("embedded got: ".con(v.str()));
                void
            });
            term.println("embedded ready");
        """.trimIndent()
        val compiled = assertNotNull(compile(src), "compilation failed")
        val runtime = VeloRuntime()
            .register(Terminal::class)
            .register("TestBridge", TestBridge::class)

        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos, true))
        var program: VeloProgram? = null
        try {
            program = runtime.start(compiled, ThreadDispatcher("test-main-dispatcher"))
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
            Terminal term = new Terminal();

            actor class Worker() {
                func process(int value, func[(int) void] done) void {
                    done(value + 1);
                    void
                };
            };

            actor[Worker] w = new Worker();
            await async w.process(41, func(int v) void {
                term.println("roundtrip: ".con(v.str()));
                void
            });
            term.println("main done");
        """.trimIndent()
        val compiled = assertNotNull(compile(src), "compilation failed")

        val bytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos -> Bytecode.write(compiled, dos) }
            baos.toByteArray()
        }
        val reread = DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            Bytecode.read(dis)
        }

        assertEquals(compiled.frames.size, reread.frames.size)
        assertEquals(compiled.natives, reread.natives)
        val output = runProgram(reread)
        assertEquals("roundtrip: 42\nmain done", output)
    }

    // ---- value-returning (non-void) callbacks ----

    @Test
    fun `a non-void callback owned by an actor returns a value to the caller`() {
        // The callback runs on its owner (an idle actor), so the blocking
        // cross-actor invocation completes instead of deadlocking.
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            actor class Doubler() {
                func make() func[(int) int] { func(int v) int { v * 2; }; };
            };

            actor[Doubler] d = new Doubler();
            func[(int) int] cb = await d.make();
            term.println(cb(21).str());
            """.trimIndent()
        )
        assertEquals("42", output)
    }

    @Test
    fun `a value-returning callback bridges two actors`() {
        // main awaits Worker; Worker invokes a callback owned by Doubler (a
        // third, idle actor) — the value flows back without deadlock.
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            actor class Doubler() {
                func make() func[(int) int] { func(int v) int { v * 2; }; };
            };
            actor class Worker() {
                func apply(func[(int) int] f, int x) int { f(x); };
            };

            actor[Doubler] d = new Doubler();
            actor[Worker] w = new Worker();
            func[(int) int] cb = await d.make();
            term.println((await w.apply(cb, 50)).str());
            """.trimIndent()
        )
        assertEquals("100", output)
    }

    @Test
    fun `value-returning callback into main resolves while main is parked at await`() {
        // VEL-11 regression: main awaits Worker, and Worker invokes a
        // value-returning callback owned by *main itself*. Under the old
        // blocking `await` this deadlocked — main blocked its dispatcher on the
        // await and could never service the callback Worker was blocked on.
        // Now `await` parks main's fiber and frees the thread, so the pump
        // services the callback, Worker unblocks, and the value flows back.
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            actor class Worker() {
                func run(func[() int] ask) int {
                    ask() * 2;
                };
            };

            int base = 21;
            actor[Worker] w = new Worker();
            int r = await async w.run(func() int { base; });
            term.println(r.str());
            """.trimIndent()
        )
        assertEquals("42", output)
    }

    @Test
    fun `a void callback remains fire-and-forget`() {
        // Regression: the void path stays fire-and-forget. `await` is a yield
        // point (VEL-11), so the posted callback runs while main is parked,
        // before main resumes and finishes its frame.
        val output = compileAndRun(
            """
            Terminal term = new Terminal();

            actor class Worker() {
                func process(int value, func[(int) void] done) void {
                    done(value * 2);
                    void
                };
            };

            actor[Worker] w = new Worker();
            await w.process(21, func(int v) void {
                term.println("callback got: ".con(v.str()));
                void
            });
            term.println("main frame done");
            """.trimIndent()
        )
        assertEquals("callback got: 42\nmain frame done", output)
    }

    // ---- helpers ----

    private val testRegistry = NativeRegistry()
        .register(Terminal::class)
        .register("TestBridge", TestBridge::class)

    private val bridgeLib = """
        TestBridge bridge = new TestBridge();
    """.trimIndent()

    private fun compile(src: String): SerializedProgram? {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val deps = emptyMap<String, String>()
        val parser = Parser(stream, SimpleInput(deps, input), nativeRegistry = testRegistry)
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
                    SerializedFrame(
                        num = it.num,
                        ops = it.ops,
                        vars = (it.varBase until it.varCounter.get()).toList(),
                    )
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
        val deps = emptyMap<String, String>()
        val parser = Parser(stream, SimpleInput(deps, input), nativeRegistry = testRegistry)
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
        val program = assertNotNull(compile(src), "compilation failed")
        return runProgram(program)
    }

    private fun runProgram(program: SerializedProgram): String {
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
