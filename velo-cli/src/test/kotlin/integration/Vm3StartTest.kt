package integration

import Terminal
import compiler.VeloCompiler
import core.NativeRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Vm3Boom {
    fun fail(): Int = error("boom")
}

class Vm3StartTest {
    @AfterTest fun reset() = TestBridge.reset()

    @Test
    fun `start keeps retained callback alive on injected dispatcher`() {
        val registry = NativeRegistry().register(Terminal::class).register(TestBridge::class)
        val program = compile(
            """
            Terminal term = new Terminal();
            TestBridge bridge = new TestBridge();
            bridge.register(func(int v) void {
                bridge.mark();
                term.println("event ".con(v.str()));
                void
            });
            term.println("ready");
            """.trimIndent(),
            registry,
        )
        val output = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(output, true))
        val dispatcher = vm2.host.ThreadDispatcher("vm3-main")
        val handle = vm3.VeloRuntime(registry).start(program, dispatcher)
        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (TestBridge.captured == null && System.currentTimeMillis() < deadline) Thread.sleep(5)
            val callback = assertNotNull(TestBridge.captured)
            assertTrue(handle.isAlive())
            callback.call(7).get(5, TimeUnit.SECONDS)
            assertTrue(TestBridge.invokeThread!!.contains("vm3-main"))
            assertEquals("ready\nevent 7", output.toString().trimEnd('\n'))
        } finally {
            TestBridge.captured?.release()
            handle.stop()
            handle.awaitTermination(5_000)
            System.setOut(old)
        }
        assertTrue(!handle.isAlive())
        dispatcher.joinFor(1_000)
        assertTrue(!dispatcher.isAlive())
    }

    @Test
    fun `start reports unhandled failure`() {
        val registry = NativeRegistry().register(Vm3Boom::class)
        val program = compile("Vm3Boom boom = new Vm3Boom(); boom.fail();", registry)
        val dispatcher = vm2.host.ThreadDispatcher("vm3-failure")
        val handle = vm3.VeloRuntime(registry).start(program, dispatcher)
        handle.awaitTermination(5_000)
        assertTrue(!handle.isAlive())
        dispatcher.joinFor(1_000)
        assertTrue(!dispatcher.isAlive())
        assertTrue(handle.failure()?.message?.contains("boom") == true)
    }

    private fun compile(source: String, registry: NativeRegistry): core.SerializedProgram {
        val file = File.createTempFile("vm3-start", ".vel").apply { writeText(source) }
        return try { VeloCompiler(registry).compile(file.path) ?: error("compile failed") } finally { file.delete() }
    }
}
