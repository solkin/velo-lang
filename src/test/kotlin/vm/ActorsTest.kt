package vm

import FileSystem
import Http
import Terminal
import Time
import compiler.CompilerFrame
import compiler.Context
import compiler.parser.InputStack
import compiler.parser.Parser
import compiler.parser.SimpleInput
import compiler.parser.StringInput
import compiler.parser.TokenStream
import utils.SerializedFrame
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end coverage of the actor pipeline: parser → compiler → bytecode →
 * VM → daemon worker thread.
 *
 * Each test compiles a small Velo source program and asserts on its captured
 * stdout. Working from source (rather than hand-built ops) keeps the tests
 * close to user-visible behaviour and exercises every layer of the
 * `actor class` / `actor[T]` / `async` / `await` / `future[T]` story.
 */
class ActorsTest {

    @Test
    fun `await async on actor method returns the produced value`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Counter() {
                int n = 0;
                func bump() int {
                    n += 1;
                    n;
                };
            };

            actor[Counter] c = new Counter();
            term.println((await async c.bump()).str);
            term.println((await async c.bump()).str);
            term.println((await async c.bump()).str);
            """.trimIndent()
        )
        assertEquals("1\n2\n3", output)
    }

    @Test
    fun `each actor owns isolated state`() {
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Counter(int start) {
                int n = start;
                func bump() int {
                    n += 1;
                    n;
                };
            };

            actor[Counter] a = new Counter(10);
            actor[Counter] b = new Counter(100);
            term.println((await async a.bump()).str);
            term.println((await async b.bump()).str);
            term.println((await async a.bump()).str);
            term.println((await async b.bump()).str);
            """.trimIndent()
        )
        assertEquals("11\n101\n12\n102", output)
    }

    @Test
    fun `arguments are structurally cloned and do not alias`() {
        // Mutating the caller's array after the call must not affect the
        // copy the actor stored.
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Bag() {
                array[int] held = new array[int]{};
                func put(array[int] xs) void {
                    held = xs;
                };
                func sum() int {
                    int total = 0;
                    int i = 0;
                    while (i < held.len()) {
                        total = total + held[i];
                        i = i + 1;
                    };
                    total;
                };
            };

            actor[Bag] b = new Bag();
            array[int] xs = new array[int]{1, 2, 3};
            await async b.put(xs);
            xs[0] = 99;
            term.println((await async b.sum()).str);
            """.trimIndent()
        )
        assertEquals("6", output)
    }

    @Test
    fun `actor field that holds another actor stays addressable across calls`() {
        // An actor whose constructor spawns and stores another actor; calling
        // a method that returns that field gives back a working actor[T].
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Counter() {
                int n = 0;
                func bump() int {
                    n += 1;
                    n;
                };
            };

            actor class Container() {
                actor[Counter] inner = new Counter();
                func get() actor[Counter] { inner; };
            };

            actor[Container] c = new Container();
            actor[Counter] x = await async c.get();
            term.println((await async x.bump()).str);
            term.println((await async x.bump()).str);
            """.trimIndent()
        )
        assertEquals("1\n2", output)
    }

    @Test
    fun `actor returns the same logical ref on repeated calls`() {
        // Container's `get()` returns the *same* internal Counter on each
        // call. Both the actor handle and the objectId must be stable, so
        // the wrapped Counter stays addressable across calls.
        val program = """
            actor class Counter() {
                int n = 0;
            };

            actor class Container() {
                actor[Counter] inner = new Counter();
                func get() actor[Counter] { inner; };
            };

            actor[Container] c = new Container();
        """.trimIndent()
        val (ctx, executor, methodIndex) = startWithSingleMethod(program, "get")
        executor.run()

        val ref = findActorRef(ctx)
        val first = ref.handle.requestCall(ref.objectId, methodVarIndex = methodIndex, args = emptyList())
        val second = ref.handle.requestCall(ref.objectId, methodVarIndex = methodIndex, args = emptyList())
        first as vm.actors.ActorValue.Ref
        second as vm.actors.ActorValue.Ref
        assertEquals(first.handle, second.handle, "wrapped Counter must live in the same actor")
        assertEquals(first.objectId, second.objectId, "wrapped Counter must reuse its objectId")
        ctx.actorRuntime.shutdownAll()
    }

    @Test
    fun `compile-time failure when async is omitted on actor method`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() {
                    func ping() int { 1; };
                };
                actor[A] a = new A();
                int n = a.ping();
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("Property 'ping' of actor[A] is not supported"),
            "Unexpected error: $msg",
        )
    }

    @Test
    fun `compile-time failure when await receives a non-future value`() {
        val ex = assertFails {
            compileOrThrow(
                """
                int x = 1;
                int y = await x;
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("await"), "Unexpected error: $msg")
        assertTrue(msg.contains("future"), "Unexpected error: $msg")
    }

    @Test
    fun `compile-time failure when actor method returns a future`() {
        // future[T] is not transferable: actor methods cannot expose it.
        val ex = assertFails {
            compileOrThrow(
                """
                actor class W() {
                    func work() int { 7; };
                };
                actor class Bad() {
                    actor[W] w = new W();
                    func dispatch() future[int] {
                        async w.work();
                    };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Bad.dispatch"), "Unexpected error: $msg")
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `compile-time failure when actor method returns non-actor class`() {
        val ex = assertFails {
            compileOrThrow(
                """
                class Pair(int a, int b) {};
                actor class A() {
                    func make() Pair { new Pair(1, 2); };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("A.make"), "Unexpected error: $msg")
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `compile-time failure when actor method takes non-actor class arg`() {
        val ex = assertFails {
            compileOrThrow(
                """
                class Pair(int a, int b) {};
                actor class A() {
                    func take(Pair p) int { p.a; };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("A.take"), "Unexpected error: $msg")
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `compile-time failure when actor constructor takes non-transferable arg`() {
        val ex = assertFails {
            compileOrThrow(
                """
                class Pair(int a, int b) {};
                actor class A(Pair p) {};
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Actor 'A' constructor"), "Unexpected error: $msg")
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `compile-time failure when actor method exposes a function value`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() {
                    func produce() func[int] {
                        func() int { 7; };
                    };
                };
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("A.produce"), "Unexpected error: $msg")
        assertTrue(msg.contains("not transferable"), "Unexpected error: $msg")
    }

    @Test
    fun `transferable containers are accepted on actor signatures`() {
        // array[int], dict[int:str], tuple[int,str], actor[T] must all pass
        // the transferability check and round-trip cleanly through await.
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Hub() {
                array[int] last = new array[int]{};
                func echo(array[int] xs, dict[int:str] tags) array[int] {
                    last = xs;
                    last;
                };
            };

            actor[Hub] h = new Hub();
            array[int] back = await async h.echo(new array[int]{1, 2, 3}, new dict[int:str]{1:"a"});
            term.println(back[0].str);
            term.println(back[1].str);
            term.println(back[2].str);
            """.trimIndent()
        )
        assertEquals("1\n2\n3", output)
    }

    @Test
    fun `async returns a future that survives across statements`() {
        // Two `async` calls fire in order, then both await yield correctly —
        // exercises the FutureRecord lifetime + cleaner registration.
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Counter(int start) {
                int n = start;
                func bump() int {
                    n += 1;
                    n;
                };
            };

            actor[Counter] a = new Counter(10);
            actor[Counter] b = new Counter(100);
            future[int] fa = async a.bump();
            future[int] fb = async b.bump();
            term.println((await fa).str);
            term.println((await fb).str);
            """.trimIndent()
        )
        assertEquals("11\n101", output)
    }

    @Test
    fun `async dispatches both actors before either await blocks`() {
        // Real parallelism check: each actor sleeps for SLEEP_MS via the
        // native Time.sleep binding. Sequential awaits would take ~2 *
        // SLEEP_MS wall time; parallel async + later awaits must finish
        // close to SLEEP_MS. The cap (1.5x) leaves slack for test machine
        // variance and JVM startup.
        val sleepMs = 200
        val src = """
            native class Time() {
                native func sleep(int ms) void;
                native func unix() int;
            };

            actor class Sleeper() {
                Time t = new Time();
                func nap(int ms) int {
                    t.sleep(ms);
                    ms;
                };
            };

            actor[Sleeper] a = new Sleeper();
            actor[Sleeper] b = new Sleeper();
            future[int] fa = async a.nap($sleepMs);
            future[int] fb = async b.nap($sleepMs);
            int x = await fa;
            int y = await fb;
        """.trimIndent()

        val frames = assertNotNull(compile(src), "compilation failed")
        val nativeRegistry = NativeRegistry()
            .register(Terminal::class)
            .register(Time::class)
            .register(FileSystem::class)
            .register(Http::class)
        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos))
        val started = System.currentTimeMillis()
        try {
            val vm = VM(nativeRegistry)
            vm.load(SimpleParser(frames))
            vm.run()
        } finally {
            System.setOut(oldOut)
        }
        val elapsed = System.currentTimeMillis() - started
        val cap = (sleepMs * 1.5).toLong()
        assertTrue(
            elapsed < cap,
            "expected parallel async to finish under ${cap}ms but took ${elapsed}ms",
        )
    }

    @Test
    fun `runtime failure inside actor surfaces as caller exception`() {
        // Calling a missing method has to fail loudly without poisoning the
        // worker — the next request on the same actor still works.
        val program = """
            actor class A() {
                func works() int { 42; };
            };
            actor[A] a = new A();
        """.trimIndent()
        val (ctx, executor, worksIndex) = startWithSingleMethod(program, "works")
        executor.run()

        val ref = findActorRef(ctx)
        val handle = ref.handle

        val ex = assertFails {
            handle.requestCall(ref.objectId, methodVarIndex = 999, args = emptyList())
        }
        assertContains(ex.message ?: "", "[actor A]")
        // Worker is still alive and serving requests.
        val ok = handle.requestCall(ref.objectId, methodVarIndex = worksIndex, args = emptyList())
        assertTrue(ok is vm.actors.ActorValue.Primitive && (ok.value as Int) == 42)
        ctx.actorRuntime.shutdownAll()
    }

    @Test
    fun `await on a completed future is idempotent`() {
        // CompletableFuture.join() is idempotent; awaiting the same FutureRecord
        // twice must yield the same value without re-running the actor method.
        // (If the body re-ran, the second value would be 2, not 1.)
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Counter() {
                int n = 0;
                func bump() int {
                    n += 1;
                    n;
                };
            };

            actor[Counter] c = new Counter();
            future[int] f = async c.bump();
            int first = await f;
            int second = await f;
            term.println(first.str);
            term.println(second.str);
            """.trimIndent()
        )
        assertEquals("1\n1", output)
    }

    @Test
    fun `actor method can fan out to other actors via async`() {
        // Boss runs a normal cross-actor async/await inside its own method
        // body. Two different worker threads are involved, so no risk of
        // self-call deadlock — this validates that nothing prevents an actor
        // from being a client of another actor.
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Worker() {
                func square(int x) int { x * x; };
            };

            actor class Boss() {
                actor[Worker] w = new Worker();
                func dispatch(int n) int {
                    future[int] f = async w.square(n);
                    await f;
                };
            };

            actor[Boss] b = new Boss();
            term.println((await async b.dispatch(7)).str);
            term.println((await async b.dispatch(9)).str);
            """.trimIndent()
        )
        assertEquals("49\n81", output)
    }

    @Test
    fun `array of futures can be awaited element by element`() {
        // future[T] is a regular Velo type; container generics work too. Tests
        // that array[future[int]] type-checks, holds the futures alive, and
        // each entry can be awaited independently. Indices need parens
        // because PROPERTY/INDEX share precedence inside the await operand.
        val output = compileAndRun(
            """
            include "lang/terminal.vel";

            actor class Counter() {
                int n = 0;
                func bump() int {
                    n += 1;
                    n;
                };
            };

            actor[Counter] a = new Counter();
            actor[Counter] b = new Counter();
            array[future[int]] futures = new array[future[int]]{
                async a.bump(),
                async b.bump()
            };
            term.println((await (futures[0])).str);
            term.println((await (futures[1])).str);
            """.trimIndent()
        )
        assertEquals("1\n1", output)
    }

    @Test
    fun `runtime failure inside actor surfaces through await async`() {
        // The compile-time error path is covered by other tests; this one
        // verifies the *runtime* path through ActorCall + FutureAwait. An
        // out-of-bounds read inside the actor's method becomes an
        // ActorResponse.Failure, which FutureAwait re-throws on the caller's
        // thread. We bypass VM.run's catch-all so the exception propagates
        // out of `executor.run()` directly.
        val frames = assertNotNull(
            compile(
                """
                actor class A() {
                    func boom() int {
                        array[int] xs = new array[int]{};
                        xs[0];
                    };
                };

                actor[A] a = new A();
                int x = await async a.boom();
                """.trimIndent()
            ),
            "compilation failed",
        )
        val (ctx, executor) = startProgram(frames)
        val ex = assertFails {
            try {
                executor.run()
            } finally {
                ctx.actorRuntime.shutdownAll()
            }
        }
        assertContains(ex.message ?: "", "[actor A]")
    }

    @Test
    fun `parse error when async is not followed by a dot`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() { func ping() int { 1; }; };
                actor[A] a = new A();
                future[int] f = async a;
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("'async' must be followed by a method call"), "Unexpected error: $msg")
    }

    @Test
    fun `parse error when async method call has no parentheses`() {
        val ex = assertFails {
            compileOrThrow(
                """
                actor class A() { func ping() int { 1; }; };
                actor[A] a = new A();
                future[int] f = async a.ping;
                """.trimIndent()
            )
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("'async' requires a method call"), "Unexpected error: $msg")
    }

    @Test
    fun `program shutdown joins actor daemon threads`() {
        val program = """
            actor class A() {
                func ping() int { 1; };
            };
            actor[A] a = new A();
        """.trimIndent()
        val frames = compile(program) ?: error("Compilation failed")
        val (ctx, executor) = startProgram(frames)
        executor.run()

        val ref = findActorRef(ctx)
        val handle = ref.handle
        assertTrue(handle.isAlive(), "actor must be alive after spawn")

        ctx.actorRuntime.shutdownAll()
        handle.joinFor(2_000)
        assertTrue(!handle.isAlive(), "actor must finish after explicit shutdown")
    }

    // ---- helpers ----

    /**
     * Compile [src], inspect the compiler context to resolve the var index
     * of [methodName] (must exist on the *last* declared actor class), and
     * start the program ready for direct ActorHandle calls.
     */
    private fun startWithSingleMethod(src: String, methodName: String): Triple<VMContext, VMExecutor, Int> {
        val input = InputStack().push(name = "test", StringInput(src))
        val stream = TokenStream(input)
        val parser = Parser(stream, SimpleInput(emptyMap(), input))
        val node = parser.parse()
        val ctx = Context(
            parent = null,
            frame = CompilerFrame(0, mutableListOf(), mutableMapOf(), AtomicInteger()),
            frameCounter = AtomicInteger(),
        )
        node.compile(ctx)

        // Pull the method's var index from the last-declared actor's class context.
        val classType = ctx.frame.vars.values
            .mapNotNull { it.type as? compiler.nodes.ClassType }
            .lastOrNull { it.isActor && it.parent != null }
            ?: error("No actor class found in $src")
        val methodVar = classType.parent!!.frame.vars[methodName]
            ?: error("Method '$methodName' not found on actor '${classType.name}'")

        val frames = ctx.frames().map {
            SerializedFrame(num = it.num, ops = it.ops, vars = it.vars.map { v -> v.value.index })
        }
        val (vmCtx, executor) = startProgram(frames)
        return Triple(vmCtx, executor, methodVar.index)
    }

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

    /**
     * Start a compiled program but stop just before running the main loop —
     * lets tests drive [vm.actors.ActorHandle] directly afterwards.
     */
    private fun startProgram(frames: List<SerializedFrame>): Pair<VMContext, VMExecutor> {
        val frameLoader = GeneralFrameLoader(frames.associateBy { it.num })
        val ctx = VMContext(
            stack = LifoStack(),
            frameLoader = frameLoader,
            memory = MemoryAreaImpl(),
            nativeRegistry = NativeRegistry(),
        )
        val main = ctx.loadFrame(0, parentVars = null) ?: error("No main frame")
        ctx.pushFrame(main)
        return ctx to VMExecutor(ctx)
    }

    private fun compileAndRun(src: String): String {
        val frames = assertNotNull(compile(src), "compilation failed")
        val nativeRegistry = NativeRegistry()
            .register(Terminal::class)
            .register(Time::class)
            .register(FileSystem::class)
            .register(Http::class)
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

    private fun findActorRef(ctx: VMContext): vm.actors.ActorRefRecord {
        val vars = ctx.currentFrame().vars.vars
        return vars.values.filterIsInstance<vm.actors.ActorRefRecord>().firstOrNull()
            ?: error("No ActorRefRecord found in main frame vars (vars=${vars.values})")
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
