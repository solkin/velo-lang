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
import kotlin.test.fail

/**
 * Language-neutral conformance corpus runner. Each `conformance/cases/**/<name>.vel`
 * is compiled once and executed on BOTH backends — the shipping [vm.VeloRuntime]
 * and the clean-room [vm2.VeloRuntime] — and every backend's stdout must equal the
 * committed `<name>.out` (and therefore the other backend's). This is the shared
 * gate that keeps every VM, on any stack, honest about behaviour.
 *
 * The corpus lives outside this module (`/conformance`) precisely so a future
 * non-JVM implementation can run the exact same cases with its own thin runner.
 *
 * A missing `.out` is auto-generated from the agreed output (only when both VMs
 * match and the program printed no `FAIL` self-check line); the run then fails
 * asking for review, so nothing is silently blessed.
 */
class ConformanceTest {

    private fun registry() = NativeRegistry()
        .register(Terminal::class)
        .register(Time::class)
        .register(FileSystem::class)
        .register(Http::class)
        .register(Socket::class)

    private fun casesDir(): File {
        for (c in listOf("conformance/cases", "../conformance/cases")) {
            File(c).takeIf { it.isDirectory }?.let { return it }
        }
        var dir: File? = File(".").absoluteFile
        repeat(6) {
            dir?.let { d ->
                File(d, "conformance/cases").takeIf { it.isDirectory }?.let { return it }
                dir = d.parentFile
            }
        }
        error("conformance/cases directory not found")
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
        return strip(baos.toString(Charsets.UTF_8))
    }

    /** Keep only program output — drop VM banner / status lines. */
    private fun strip(out: String): String = out.lineSequence()
        .filterNot { line ->
            line.startsWith("Program ended") || line.startsWith("Program halted") ||
                line.startsWith("VM stopped") || line.startsWith("Parsed in") ||
                line.startsWith("Compiled in") || line.startsWith("Bytecode ") ||
                line.startsWith("✓ Program") || line.startsWith("⏹ Program")
        }
        .joinToString("\n")
        .trimEnd('\n')

    private data class Backend(val name: String, val run: (SerializedProgram, NativeRegistry) -> Unit)

    private val backends = listOf(
        Backend("velo-vm") { p, r -> vm.VeloRuntime(r).run(p) },
        Backend("velo-vm2") { p, r -> vm2.VeloRuntime(r).run(p) },
        Backend("velo-vm3") { p, r -> vm3.VeloRuntime(r).run(p) },
    )

    private fun marker(src: String): String? =
        src.lineSequence().firstOrNull()?.let { first ->
            Regex("""#\s*conformance:\s*(\S+)""").find(first)?.groupValues?.get(1)
        }

    @Test
    fun `conformance corpus matches on every backend`() {
        val dir = casesDir()
        val cases = dir.walkTopDown().filter { it.isFile && it.extension == "vel" }
            .sortedBy { it.path }.toList()
        check(cases.isNotEmpty()) { "no conformance cases found under ${dir.absolutePath}" }

        val failures = mutableListOf<String>()
        val generated = mutableListOf<String>()
        var checks = 0

        for (vel in cases) {
            val rel = vel.relativeTo(dir).path
            val src = vel.readText()
            when (marker(src)) {
                "skip" -> continue
                "vm2-only" -> {}
            }
            val active = if (marker(src) == "vm2-only") backends.filter { it.name == "velo-vm2" } else backends

            val program = VeloCompiler(registry()).compile(vel.path)
            if (program == null) {
                failures += "$rel: compilation failed"
                continue
            }

            val outputs = active.map { backend ->
                backend.name to runCatching { capture { backend.run(program, registry()) } }
                    .getOrElse { ex -> "<threw: ${ex.message}>" }
            }

            val failing = outputs.filter { it.second.lineSequence().any { l -> l.startsWith("FAIL") } }
            if (failing.isNotEmpty()) {
                failures += "$rel: self-check FAIL on ${failing.joinToString { it.first }}\n${failing.first().second}"
                continue
            }
            val distinct = outputs.map { it.second }.distinct()
            if (distinct.size > 1) {
                failures += "$rel: backends diverge\n" +
                    outputs.joinToString("\n") { "--- ${it.first} ---\n${it.second}" }
                continue
            }

            val agreed = distinct.first()
            val outFile = File(vel.parentFile, "${vel.nameWithoutExtension}.out")
            if (!outFile.exists()) {
                outFile.writeText(if (agreed.isEmpty()) "" else agreed + "\n")
                generated += rel
                continue
            }
            val expected = outFile.readText().trimEnd('\n')
            for ((name, output) in outputs) {
                checks++
                if (output != expected) {
                    failures += "$rel [$name]: mismatch\n--- expected ---\n$expected\n--- actual ---\n$output"
                }
            }
        }

        println("conformance: ran ${cases.size} case(s), $checks backend-check(s) across ${backends.size} VMs")
        val report = buildString {
            if (failures.isNotEmpty()) {
                append("${failures.size} conformance failure(s):\n\n")
                append(failures.joinToString("\n\n"))
            }
            if (generated.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("recorded ${generated.size} new expected output(s) — review and re-run:\n")
                append(generated.joinToString("\n"))
            }
        }
        if (report.isNotEmpty()) fail(report)
    }
}
