package vm2.golden

import compiler.VeloCompiler
import core.NativeRegistry
import vm2.VeloRuntime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden tests for velo-vm2: compile each `.vel` in the shared golden
 * directory with the real front-end, run it on vm2, and compare captured
 * stdout against the matching `.golden`.
 */
class Vm2GoldenTest {

    private val goldenDir = sequenceOf(
        File("../velo-cli/src/test/resources/golden"),
        File("velo-cli/src/test/resources/golden"),
    ).firstOrNull { it.isDirectory } ?: error("golden directory not found")

    /** Golden cases that need features not yet implemented in vm2. */
    private val pending = emptySet<String>()

    private fun runGolden(name: String): String {
        val source = File(goldenDir, "$name.vel")
        val registry = NativeRegistry().register(Terminal::class)
        val program = VeloCompiler(registry).compile(source.path)
            ?: fail("compilation failed for $name")

        val baos = ByteArrayOutputStream()
        val oldOut = System.out
        System.setOut(PrintStream(baos))
        try {
            VeloRuntime(registry).run(program)
        } finally {
            System.setOut(oldOut)
        }
        return baos.toString().trimEnd('\n')
    }

    @Test
    fun `all golden cases`() {
        val cases = goldenDir.listFiles { f -> f.extension == "vel" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .filter { File(goldenDir, "$it.golden").exists() }
            .sorted()

        val failures = mutableListOf<String>()
        for (name in cases) {
            if (name in pending) continue
            val expected = File(goldenDir, "$name.golden").readText().trimEnd('\n')
            val actual = try {
                runGolden(name)
            } catch (e: Throwable) {
                failures += "$name: ERROR ${e.message}"
                continue
            }
            if (actual == expected) {
                println("PASS $name")
            } else {
                failures += "$name:\n--- expected ---\n$expected\n--- actual ---\n$actual"
            }
        }
        if (failures.isNotEmpty()) fail("${failures.size} golden case(s) failed:\n\n" + failures.joinToString("\n\n"))
    }
}
