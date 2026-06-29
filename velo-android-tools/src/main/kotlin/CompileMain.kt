import compiler.VeloCompiler
import core.Bytecode
import core.NativeRegistry
import ui.registerUiStubs
import java.io.File

/**
 * Build-time compiler entry point for the `velo-android` sample task — knows the Android
 * UI natives in addition to the standard pool, without an Android SDK on the classpath.
 *
 *   CompileMainKt <program.vel> <out.vbc>
 *
 * The registry is the standard CLI pool (`registerDefaults`) plus the pure-JVM
 * [ui.Ui] / [ui.View] signature stubs so `Ui`/`View` calls type-check. The resulting
 * `.vbc` links against the real Material3 implementations on device. Lives here, not in
 * the CLI, so the command-line tool never advertises UI it can't draw.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: CompileMainKt <program.vel> <out.vbc>" }
    val (input, output) = args

    val natives = NativeRegistry()
        .registerDefaults()
        .registerUiStubs()
        .register(ui.Colors::class)
        .register(ui.Icons::class)
        .register(ui.TextStyles::class)

    val program = VeloCompiler(natives).compile(input)
        ?: error("compilation failed for $input")
    Bytecode.write(program, File(output))
}
