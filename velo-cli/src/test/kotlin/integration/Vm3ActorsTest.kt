package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class Vm3Delay {
    fun sleep(ms: Int) = Thread.sleep(ms.toLong())
}

/** Threading, isolation and callback routing for the compact actor runtime. */
class Vm3ActorsTest {
    @AfterTest fun reset() = TestBridge.reset()

    @Test
    fun `actors use injected pool concurrently`() {
        lateinit var output: String
        val elapsed = measureTimeMillis {
            output = run(
                """
                Terminal term = new Terminal();
                actor class Worker() {
                    Vm3Delay delay = new Vm3Delay();
                    func work(int ms, int result) int { delay.sleep(ms); return result; };
                };
                actor[Worker] a = new Worker();
                actor[Worker] b = new Worker();
                future[int] fa = async a.work(180, 1);
                future[int] fb = async b.work(180, 2);
                term.println((await fa).str());
                term.println((await fb).str());
                """.trimIndent(),
                threaded = true,
                parallelism = 2,
            )
        }
        assertEquals("1\n2", output)
        assertTrue(elapsed < 330, "actors serialized: ${elapsed}ms")
    }

    @Test
    fun `array argument is structurally cloned`() {
        assertEquals(
            "99\n1",
            run(
                """
                Terminal term = new Terminal();
                actor class Mutator() {
                    func change(array[int] a) int { a[0] = 99; return a[0]; };
                };
                array[int] source = new array[int]{1};
                actor[Mutator] m = new Mutator();
                term.println((await m.change(source)).str());
                term.println(source[0].str());
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `nested data class payload is copied in both directions`() {
        assertEquals(
            "99\n1",
            run(
                """
                Terminal term = new Terminal();
                data class Bag(array[int] values) {};
                actor class Mutator() {
                    func change(Bag bag) Bag { bag.values[0] = 99; return bag; };
                };
                Bag source = new Bag(new array[int]{1});
                actor[Mutator] m = new Mutator();
                Bag changed = await m.change(source);
                term.println(changed.values[0].str());
                term.println(source.values[0].str());
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `actor callback runs on main owner while await is parked`() {
        val mainThread = Thread.currentThread().name
        assertEquals(
            "callback 7\nafter",
            run(
                """
                Terminal term = new Terminal();
                TestBridge bridge = new TestBridge();
                actor class Firer() {
                    func fire(func[(int) void] cb) void { cb(7); void };
                };
                actor[Firer] f = new Firer();
                await f.fire(func(int v) void {
                    bridge.mark();
                    term.println("callback ".con(v.str()));
                    void
                });
                term.println("after");
                """.trimIndent(),
                threaded = true,
                parallelism = 2,
            ),
        )
        assertEquals(mainThread, TestBridge.invokeThread)
    }

    @Test
    fun `await suspends actor fiber on a single-thread pool`() {
        assertEquals(
            "42",
            run(
                """
                Terminal term = new Terminal();
                actor class Leaf() { func value() int { return 42; }; };
                actor class Parent() {
                    func relay(actor[Leaf] leaf) int { return await leaf.value(); };
                };
                actor[Leaf] leaf = new Leaf();
                actor[Parent] parent = new Parent();
                term.println((await parent.relay(leaf)).str());
                """.trimIndent(),
                threaded = true,
                parallelism = 1,
            ),
        )
    }

    @Test
    fun `callback returned by actor executes on actor owner`() {
        assertEquals(
            "42",
            run(
                """
                Terminal term = new Terminal();
                actor class Factory() {
                    func make(int factor) func[(int) int] {
                        return func(int value) int { return value * factor; };
                    };
                };
                actor[Factory] factory = new Factory();
                func[(int) int] twice = await factory.make(2);
                term.println(twice(21).str());
                """.trimIndent(),
                threaded = true,
                parallelism = 1,
            ),
        )
    }

    @Test
    fun `nested actor spawn does not deadlock a single-thread pool`() {
        assertEquals(
            "42",
            run(
                """
                Terminal term = new Terminal();
                actor class Child() { func value() int { return 42; }; };
                actor class Parent() {
                    func create() int {
                        actor[Child] child = new Child();
                        return await child.value();
                    };
                };
                actor[Parent] parent = new Parent();
                term.println((await parent.create()).str());
                """.trimIndent(),
                threaded = true,
                parallelism = 1,
            ),
        )
    }

    @Test
    fun `callback arrays are cloned in both directions`() {
        assertEquals(
            "16\n1",
            run(
                """
                Terminal term = new Terminal();
                actor class Worker() {
                    func use(array[int] values, func[(array[int]) int] cb) int {
                        values[0] = 9;
                        int fromCallback = cb(values);
                        return values[0] + fromCallback;
                    };
                };
                array[int] source = new array[int]{1};
                actor[Worker] worker = new Worker();
                int result = await worker.use(source, func(array[int] values) int {
                    values[0] = 7;
                    return values[0];
                });
                term.println(result.str());
                term.println(source[0].str());
                """.trimIndent(),
                threaded = true,
                parallelism = 1,
            ),
        )
    }

    @Test
    fun `run stats include actor instructions`() {
        val registry = NativeRegistry()
        val file = File.createTempFile("vm3-actor-stats", ".vel").apply {
            writeText(
                """
                actor class Worker() {
                    func work() int {
                        int i = 0;
                        while (i < 100) { i += 1; };
                        return i;
                    };
                };
                actor[Worker] worker = new Worker();
                int result = await worker.work();
                """.trimIndent(),
            )
        }
        val program = try { VeloCompiler(registry).compile(file.path) ?: error("compile failed") } finally { file.delete() }
        val stats = vm3.VeloRuntime(registry).run(program)
        assertTrue(stats.instructions > 400, "actor instructions missing: ${stats.instructions}")
    }

    @Test
    fun `fire and forget callback failure is program fatal`() {
        assertFails {
            run(
                """
                Vm3Boom boom = new Vm3Boom();
                actor class Firer() {
                    func fire(func[() void] cb) void { cb(); void };
                };
                actor[Firer] firer = new Firer();
                await firer.fire(func() void { boom.fail(); void });
                """.trimIndent(),
                threaded = true,
                parallelism = 1,
            )
        }
    }

    private fun run(source: String, threaded: Boolean = false, parallelism: Int = 2): String {
        val registry = NativeRegistry()
            .register(Terminal::class)
            .register(Vm3Delay::class)
            .register(Vm3Boom::class)
            .register(TestBridge::class)
        val file = File.createTempFile("vm3-actors", ".vel").apply { writeText(source) }
        val program = try { VeloCompiler(registry).compile(file.path) ?: error("compile failed") } finally { file.delete() }
        val runtime = vm3.VeloRuntime(registry)
        if (threaded) runtime.actorPlacement { vm2.host.PooledDispatcherFactory(parallelism = parallelism) }
        val bytes = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(bytes))
        try { runtime.run(program) } finally { System.setOut(old) }
        return bytes.toString().trimEnd('\n')
    }
}
