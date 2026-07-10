package integration

import FileSystem
import Http
import Socket
import Terminal
import Time
import compiler.VeloCompiler
import core.NativeRegistry
import core.SerializedProgram
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Parity gate for the clean-room **velo-vm2** backend: every deterministic,
 * non-interactive demo in `src/main/resources` is compiled once and run on both
 * the legacy `vm.VeloRuntime` and `vm2.VeloRuntime`; their stdout must match
 * byte for byte. This proves vm2 reproduces the shipping VM across real
 * programs (closures, generics, actors, callbacks, data classes, pointers,
 * compression kernels, …), not just the golden snippets.
 */
class Vm2ParityTest {

    private val demoDir = sequenceOf(
        File("src/main/resources"),
        File("velo-cli/src/main/resources"),
    ).firstOrNull { it.isDirectory } ?: error("demo dir not found")

    // Excluded: interactive (term.input) and host-coupled / non-deterministic
    // natives (Time/Http/Socket/FileSystem) whose output isn't a pure function
    // of the source.
    private fun isDeterministic(src: String): Boolean =
        !src.contains("input(") &&
            !Regex("""\b(Time|Http|Socket|FileSystem)\b""").containsMatchIn(src)

    private fun registry() = NativeRegistry()
        .register(Terminal::class)
        .register(Time::class)
        .register(FileSystem::class)
        .register(Http::class)
        .register(Socket::class)

    private fun capture(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try { block() } finally { System.setOut(old) }
        return strip(baos.toString())
    }

    /** Drop VM banner/status lines so only program output is compared. */
    private fun strip(out: String): String = out.lineSequence()
        .filterNot { line ->
            line.startsWith("Program ended") || line.startsWith("Program halted") ||
                line.startsWith("VM stopped") || line.startsWith("Parsed in") ||
                line.startsWith("Compiled in") || line.startsWith("Bytecode ") ||
                line.startsWith("✓ Program") || line.startsWith("⏹ Program")
        }
        .joinToString("\n")
        .trimEnd('\n')

    @Test
    fun `demos produce identical output on the legacy VM and vm2`() {
        val demos = demoDir.listFiles { f -> f.extension == "vel" }.orEmpty()
            .filter { isDeterministic(it.readText()) }
            .sortedBy { it.name }
        check(demos.size >= 15) { "expected the demo corpus, found ${demos.size}" }

        val failures = mutableListOf<String>()
        var compared = 0
        for (demo in demos) {
            val program: SerializedProgram? = VeloCompiler(registry()).compile(demo.path)
            if (program == null) {
                failures += "${demo.name}: compilation failed"
                continue
            }
            val legacy = capture { vm.VeloRuntime(registry()).run(program) }
            val fresh = capture { vm2.VeloRuntime(registry()).run(program) }
            val compact = capture { vm3.VeloRuntime(registry()).run(program) }
            if (legacy == fresh && legacy == compact) {
                compared++
                println("PARITY ✓ ${demo.name}")
            } else {
                failures += "${demo.name}:\n--- legacy ---\n$legacy\n--- vm2 ---\n$fresh\n--- vm3 ---\n$compact"
            }
        }
        println("compared $compared demos")
        if (failures.isNotEmpty()) fail("${failures.size} demo(s) diverged:\n\n" + failures.joinToString("\n\n"))
    }
}
