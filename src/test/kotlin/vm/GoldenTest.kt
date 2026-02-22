package vm

import FileSystem
import Http
import Terminal
import Time
import compiler.CompilerFrame
import compiler.Context
import compiler.parser.FileInput
import compiler.parser.Parser
import compiler.parser.TokenStream
import utils.SerializedFrame
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Golden tests for VM - compares program output with expected golden files.
 * 
 * Each test case consists of:
 * - A .vel source file in src/test/resources/golden/
 * - A .golden file with expected output
 * 
 * To update golden files when output changes intentionally:
 * 1. Run the test to see actual output
 * 2. Update the .golden file with new expected output
 */
class GoldenTest {

    private val goldenDir = getGoldenDir()

    private fun getGoldenDir(): File {
        // Try to find from test resources
        val resourceUrl = this::class.java.classLoader.getResource("golden")
        if (resourceUrl != null) {
            return File(resourceUrl.toURI())
        }
        // Fallback to project path
        return File("src/test/resources/golden")
    }

    private fun compile(sourceFile: File): List<SerializedFrame>? {
        val input = FileInput(dir = sourceFile.parent).apply {
            load(name = sourceFile.name)
        }
        val stream = TokenStream(input)
        val parser = Parser(stream, depLoader = input)
        val node = parser.parse()

        val ctx = Context(
            parent = null,
            frame = CompilerFrame(
                num = 0,
                ops = mutableListOf(),
                vars = mutableMapOf(),
                varCounter = AtomicInteger()
            ),
            frameCounter = AtomicInteger(),
        )

        return try {
            node.compile(ctx)
            ctx.frames().map {
                SerializedFrame(
                    num = it.num,
                    ops = it.ops,
                    vars = it.vars.map { v -> v.value.index }
                )
            }
        } catch (ex: Throwable) {
            System.err.println("Compilation error: ${ex.message}")
            ex.printStackTrace(System.err)
            null
        }
    }

    private fun runAndCaptureOutput(frames: List<SerializedFrame>): String {
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

    /**
     * Extract only program output, removing VM messages
     */
    private fun extractProgramOutput(fullOutput: String): String {
        val lines = fullOutput.lines()
        val result = StringBuilder()
        
        for (line in lines) {
            // Skip VM system messages
            if (line.startsWith("Program ended") ||
                line.startsWith("Program halted") ||
                line.startsWith("VM stopped") ||
                line.startsWith("Parsed in") ||
                line.startsWith("Compiled in") ||
                line.startsWith("✓ Program") ||
                line.startsWith("⏹ Program")) {
                continue
            }
            result.appendLine(line)
        }

        // Remove trailing newlines but keep internal structure
        return result.toString().trimEnd('\n')
    }

    private fun runGoldenTest(testName: String) {
        val sourceFile = File(goldenDir, "$testName.vel")
        val goldenFile = File(goldenDir, "$testName.golden")

        if (!sourceFile.exists()) {
            fail("Source file not found: ${sourceFile.absolutePath}")
        }
        if (!goldenFile.exists()) {
            fail("Golden file not found: ${goldenFile.absolutePath}")
        }

        val frames = compile(sourceFile)
        if (frames == null) {
            fail("Compilation failed for $testName")
        }

        val actualOutput = runAndCaptureOutput(frames)
        val expectedOutput = goldenFile.readText().trimEnd('\n')

        assertEquals(
            expectedOutput,
            actualOutput,
            "Golden test failed for '$testName'\n" +
            "Expected:\n$expectedOutput\n\n" +
            "Actual:\n$actualOutput"
        )
    }

    // ========== Golden Tests ==========

    @Test
    fun `golden - hello world`() {
        runGoldenTest("hello")
    }

    @Test
    fun `golden - arithmetic operations`() {
        runGoldenTest("arithmetic")
    }

    @Test
    fun `golden - variables`() {
        runGoldenTest("variables")
    }

    @Test
    fun `golden - conditionals`() {
        runGoldenTest("conditionals")
    }

    @Test
    fun `golden - loops`() {
        runGoldenTest("loops")
    }

    @Test
    fun `golden - functions`() {
        runGoldenTest("functions")
    }

    @Test
    fun `golden - arrays`() {
        runGoldenTest("arrays")
    }

    @Test
    fun `golden - strings`() {
        runGoldenTest("strings")
    }

    @Test
    fun `golden - classes`() {
        runGoldenTest("classes")
    }

    @Test
    fun `golden - fibonacci`() {
        runGoldenTest("fibonacci")
    }

    @Test
    fun `golden - nested classes`() {
        runGoldenTest("nested-classes")
    }

    @Test
    fun `golden - nested functions`() {
        runGoldenTest("nested-functions")
    }

    @Test
    fun `golden - variable scope`() {
        runGoldenTest("variable-scope")
    }

    @Test
    fun `golden - generics`() {
        runGoldenTest("generics")
    }

    // ========== Utility Methods ==========

    /**
     * Run all golden tests from files in the golden directory.
     * Useful for discovering new test cases.
     */
    @Test
    fun `discover and run all golden tests`() {
        val velFiles = goldenDir.listFiles { file ->
            file.isFile && file.extension == "vel"
        } ?: emptyArray()

        val failures = mutableListOf<String>()

        for (velFile in velFiles) {
            val testName = velFile.nameWithoutExtension
            val goldenFile = File(goldenDir, "$testName.golden")

            if (!goldenFile.exists()) {
                println("Skipping $testName - no golden file")
                continue
            }

            try {
                runGoldenTest(testName)
                println("✓ $testName passed")
            } catch (e: AssertionError) {
                failures.add("$testName: ${e.message}")
                println("✗ $testName failed")
            } catch (e: Exception) {
                failures.add("$testName: ${e.message}")
                println("✗ $testName error: ${e.message}")
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size} golden tests failed:\n${failures.joinToString("\n")}")
        }
    }
}

