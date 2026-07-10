package org.velo.android.engine

import compiler.VeloCompiler
import core.NativeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vm3.VeloRuntime
import java.io.File

/**
 * Verifies the compact **velo-vm3** backend against the Android app's own
 * host natives and the `core` Dispatcher SPI — the same embedding path a
 * file-picked `.vbc` takes on device, run as a pure-JVM host test.
 *
 *  - [runsAndroidNativesOnVm3] proves vm3 links and executes the app's
 *    Terminal/Time/FileSystem natives end to end.
 *  - [threadedActorsOnVm3] proves pluggable actor placement: with a pooled
 *    host dispatcher (the same design as [AndroidActorDispatcherFactory], but
 *    on the portable `core.DispatcherFactory` SPI) actors run off the main
 *    thread while the main fiber drives the Terminal.
 */
class Vm3OnAndroidTest {

    private fun registry() = NativeRegistry()
        .register("Terminal", AndroidTerminal::class)
        .register("Time", VeloTime::class)
        .register("FileSystem", VeloFileSystem::class)
        .register("Http", VeloHttp::class)
        .register("Socket", VeloSocket::class)

    private fun run(source: String, threaded: Boolean): String {
        val velFile = File.createTempFile("vm3-android", ".vel").apply { writeText(source) }
        try {
            val registry = registry()
            val program = VeloCompiler(registry).compile(velFile.absolutePath) ?: error("compilation failed")
            val output = StringBuilder()
            AndroidTerminal.current.set(TerminalBinding(onOutput = { output.append(it) }, stdin = HostStdin()))
            try {
                val runtime = VeloRuntime(registry)
                if (threaded) runtime.actorPlacement { AndroidActorDispatcherFactory() }
                runtime.run(program)
            } finally {
                AndroidTerminal.current.remove()
            }
            return output.toString()
        } finally {
            velFile.delete()
        }
    }

    @Test
    fun runsAndroidNativesOnVm3() {
        val dataFile = File.createTempFile("vm3-e2e", ".txt").apply { delete() }
        val path = dataFile.absolutePath.replace("\\", "\\\\")
        try {
            val source = """
                Terminal term = new Terminal();
                FileSystem fs = new FileSystem();
                Time time = new Time();
                time.sleep(1);
                fs.write("$path", "hello-vm3");
                term.print(fs.read("$path"));
            """.trimIndent()
            assertEquals("hello-vm3", run(source, threaded = false))
        } finally {
            dataFile.delete()
        }
    }

    @Test
    fun threadedActorsOnVm3() {
        // Actors compute on pool threads; the main fiber prints the awaited
        // results (Terminal is bound to the main thread only).
        val source = """
            Terminal term = new Terminal();
            actor class Adder(int base) {
                func add(int x) int { return base + x; };
            };
            actor[Adder] a = new Adder(100);
            actor[Adder] b = new Adder(200);
            term.println((await async a.add(1)).str());
            term.println((await async b.add(2)).str());
        """.trimIndent()
        val out = run(source, threaded = true).trim().lines()
        assertEquals(listOf("101", "202"), out)
    }
}
