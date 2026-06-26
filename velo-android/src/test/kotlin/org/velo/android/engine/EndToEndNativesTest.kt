package org.velo.android.engine

import compiler.VeloCompiler
import core.NativeRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import vm.VeloRuntime
import java.io.File

/**
 * End-to-end host test: compiles a `.vel` program that exercises Terminal + Time +
 * FileSystem against the app's natives, then runs it on [VeloRuntime] with the same
 * registry — exactly the path a file-picked `.vbc` takes on the device, minus Android.
 * Proves the natives both link and execute, not just that they introspect.
 */
class EndToEndNativesTest {

    private fun registry() = NativeRegistry()
        .register("Terminal", AndroidTerminal::class)
        .register("Time", VeloTime::class)
        .register("FileSystem", VeloFileSystem::class)
        .register("Http", VeloHttp::class)
        .register("Socket", VeloSocket::class)

    @Test
    fun runsProgramUsingTerminalAndFileSystem() {
        val dataFile = File.createTempFile("velo-e2e", ".txt").apply { delete() }
        val path = dataFile.absolutePath.replace("\\", "\\\\")

        val source = """
            Terminal term = new Terminal();
            FileSystem fs = new FileSystem();
            Time time = new Time();
            time.sleep(1);
            fs.write("$path", "hello-velo");
            term.print(fs.read("$path"));
        """.trimIndent()

        val velFile = File.createTempFile("velo-e2e", ".vel").apply { writeText(source) }

        try {
            val registry = registry()
            val program = VeloCompiler(registry).compile(velFile.absolutePath)
                ?: error("compilation failed")

            val output = StringBuilder()
            val binding = TerminalBinding(onOutput = { output.append(it) }, stdin = HostStdin())
            AndroidTerminal.current.set(binding)
            try {
                VeloRuntime(registry).run(program)
            } finally {
                AndroidTerminal.current.remove()
            }

            assertEquals("hello-velo", output.toString())
        } finally {
            velFile.delete()
            dataFile.delete()
        }
    }
}
