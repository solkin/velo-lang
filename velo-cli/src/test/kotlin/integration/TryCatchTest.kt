package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import core.SerializedProgram
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * VEL-9 try/catch/throw behaviour. Every case compiles one program and runs it
 * on each VM that supports the feature, asserting the output matches *and* is
 * identical across VMs (a focused parity gate on top of the conformance corpus).
 * velo-vm3 joins the list once it gains the feature.
 */
/** A host class whose method throws — exercises the native-failure path. */
class Boom {
    fun bang(): Int = throw RuntimeException("host exploded")
}

class TryCatchTest {

    private fun registry() = NativeRegistry().register(Terminal::class).register(Boom::class)

    private val backends: List<Pair<String, (SerializedProgram) -> Unit>> = listOf(
        "velo-vm" to { p -> vm.VeloRuntime(registry()).run(p) },
        "velo-vm2" to { p -> vm2.VeloRuntime(registry()).run(p) },
        "velo-vm3" to { p -> vm3.VeloRuntime(registry()).run(p) },
    )

    private fun compile(source: String): SerializedProgram {
        val tmp = File.createTempFile("velo-try", ".vel")
        tmp.writeText(source)
        return (VeloCompiler(registry()).compile(tmp.path) ?: fail("compilation failed")).also { tmp.delete() }
    }

    /** True if [source] compiles (parses + type-checks). */
    private fun compiles(source: String): Boolean {
        val tmp = File.createTempFile("velo-try", ".vel")
        tmp.writeText(source)
        return try {
            VeloCompiler(registry()).compile(tmp.path) != null
        } catch (_: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    private fun capture(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setOut(old)
        }
        return baos.toString(Charsets.UTF_8).lineSequence()
            .filterNot { it.isBlank() || it.startsWith("✓") || it.startsWith("⏹") || it.startsWith("!!") }
            .joinToString("\n")
    }

    /** Run [source] on every backend, assert they agree, return the shared output. */
    private fun run(source: String): String {
        val program = compile(source)
        val outputs = backends.map { (name, backend) -> name to capture { backend(program) } }
        val distinct = outputs.map { it.second }.distinct()
        assertTrue(
            distinct.size == 1,
            "backends diverge:\n" + outputs.joinToString("\n") { "--- ${it.first} ---\n${it.second}" },
        )
        return distinct.first()
    }

    private val prelude = "Terminal term = new Terminal()\n"

    @Test
    fun `catches division by zero as arithmetic`() {
        assertEquals("arithmetic", run(prelude + "try { int x = 1 / 0 } catch (Error e) { term.println(e.kind) }"))
    }

    @Test
    fun `catches out-of-bounds as bounds`() {
        assertEquals(
            "bounds",
            run(prelude + "try { array[int] a = new array[int](2); int y = a[9] } catch (Error e) { term.println(e.kind) }"),
        )
    }

    @Test
    fun `try body runs and catch is skipped when no error`() {
        assertEquals(
            "ok\nafter",
            run(prelude + "try { term.println(\"ok\") } catch (Error e) { term.println(\"BUG\") }\nterm.println(\"after\")"),
        )
    }

    @Test
    fun `throw carries kind and message to catch`() {
        assertEquals(
            "io / disk gone",
            run(prelude + "try { throw new Error(\"io\", \"disk gone\") } catch (Error e) { term.println(\"\${e.kind} / \${e.message}\") }"),
        )
    }

    @Test
    fun `throw string literal uses ERR_GENERIC`() {
        assertEquals(
            "generic / boom",
            run(prelude + "try { throw \"boom\" } catch (Error e) { term.println(\"\${e.kind} / \${e.message}\") }"),
        )
    }

    @Test
    fun `nested try — inner catches, its throw reaches the outer`() {
        assertEquals(
            "inner\nouter",
            run(
                prelude +
                    "try {\n" +
                    "  try { throw new Error(\"a\", \"1\") } catch (Error e) { term.println(\"inner\"); throw new Error(\"b\", \"2\") }\n" +
                    "} catch (Error e) { term.println(\"outer\") }",
            ),
        )
    }

    @Test
    fun `re-throw with throw e propagates the same error`() {
        assertEquals(
            "orig",
            run(
                prelude +
                    "try {\n" +
                    "  try { throw \"orig\" } catch (Error e) { throw e }\n" +
                    "} catch (Error e) { term.println(e.message) }",
            ),
        )
    }

    @Test
    fun `try inside a loop recovers each iteration`() {
        assertEquals(
            "0\ncaught\n2",
            run(
                prelude +
                    "int i = 0\n" +
                    "while (i < 3) {\n" +
                    "  try { if (i == 1) { throw \"x\" }; term.println(i.str()) } catch (Error e) { term.println(\"caught\") }\n" +
                    "  i = i + 1\n" +
                    "}",
            ),
        )
    }

    @Test
    fun `a failing native call is caught as native`() {
        assertEquals(
            "native",
            run(prelude + "Boom b = new Boom()\ntry { int x = b.bang() } catch (Error e) { term.println(e.kind) }"),
        )
    }

    @Test
    fun `await of a failing actor is caught as actor`() {
        assertEquals(
            "actor",
            run(
                "actor class Worker() { func boom() int { throw new Error(\"w\", \"kaboom\"); return 0 } }\n" +
                    prelude +
                    "actor[Worker] w = new Worker()\n" +
                    "try { int r = await async w.boom() } catch (Error e) { term.println(e.kind) }",
            ),
        )
    }

    @Test
    fun `actor awaiting a failing actor inside a try catches it (suspended fiber)`() {
        // The outer actor genuinely suspends on the await (unlike main, which
        // blocks-pumps), so this exercises the resume path — a failed await must
        // still reach a try that wraps it.
        assertEquals(
            "caught actor",
            run(
                "actor class Inner() { func boom() int { throw new Error(\"i\", \"fail\"); return 0 } }\n" +
                    "actor class Outer() {\n" +
                    "  func work() str {\n" +
                    "    actor[Inner] inner = new Inner()\n" +
                    "    str out = \"no error\"\n" +
                    "    try { int r = await async inner.boom() } catch (Error e) { out = \"caught \" + e.kind }\n" +
                    "    return out\n" +
                    "  }\n" +
                    "}\n" +
                    prelude +
                    "actor[Outer] o = new Outer()\n" +
                    "str result = await async o.work()\n" +
                    "term.println(result)",
            ),
        )
    }

    @Test
    fun `throw unwinds across a function call to the caller's try`() {
        assertEquals(
            "caught boom",
            run(
                "func risky() void { throw new Error(\"deep\", \"boom\") }\n" +
                    prelude +
                    "try { risky() } catch (Error e) { term.println(\"caught \" + e.message) }",
            ),
        )
    }

    @Test
    fun `throwing a non-Error is a compile error`() {
        assertFalse(compiles(prelude + "throw 42"))
    }

    @Test
    fun `catching a non-Error type is a compile error`() {
        assertFalse(compiles(prelude + "try { throw \"x\" } catch (Widget e) { term.println(\"x\") }"))
    }

    @Test
    fun `the catch variable is not visible after the block`() {
        assertFalse(
            compiles(prelude + "try { throw \"x\" } catch (Error e) { term.println(e.kind) }\nterm.println(e.kind)"),
        )
    }

    @Test
    fun `break out of a try leaves no stale handler`() {
        // break jumps over the try's normal-exit TryLeave; without popping the
        // handler it would linger and wrongly catch a later error in this frame.
        val program = compile(
            prelude +
                "int i = 0\n" +
                "while (i < 3) {\n" +
                "  try { i = i + 1; break } catch (Error e) { term.println(\"STALE\") }\n" +
                "}\n" +
                "term.println(\"after i=\" + i.str())\n" +
                "throw \"boom\"",
        )
        for ((name, backend) in backends) {
            val baos = ByteArrayOutputStream()
            val old = System.out
            System.setOut(PrintStream(baos, true, "UTF-8"))
            val threw = try {
                backend(program); false
            } catch (_: Throwable) {
                true
            } finally {
                System.setOut(old)
            }
            val out = baos.toString(Charsets.UTF_8)
            assertTrue(threw, "$name: an uncaught throw after the loop must be fatal")
            assertFalse(out.contains("STALE"), "$name: break must not leave a stale try handler")
            assertTrue(out.contains("after i=1"), "$name: the loop should run exactly once, got: $out")
        }
    }

    @Test
    fun `uncaught throw is fatal on every backend`() {
        val program = compile(prelude + "throw new Error(\"x\", \"fatal boom\")")
        for ((name, backend) in backends) {
            val ex = runCatching { capture { backend(program) } }.exceptionOrNull()
            assertTrue(ex != null, "$name: uncaught throw must propagate as a failure")
            assertTrue(
                (ex.message ?: "").contains("fatal boom"),
                "$name: fatal failure should carry the message, got: ${ex.message}",
            )
        }
    }
}
