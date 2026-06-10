import compiler.VeloCompiler
import core.Bytecode
import core.NativeRegistry
import core.SerializedProgram
import vm.VeloRuntime
import java.io.File

/**
 * The Velo command line:
 *
 *   velo program.vel              compile and run
 *   velo program.vel out.vbc      compile to bytecode
 *   velo program.vbc              run pre-compiled bytecode
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: velo <program.vel | program.vbc> [out.vbc]")
        return
    }
    val path = args[0]
    val out = args.getOrNull(1)

    val natives = NativeRegistry().registerDefaults()

    val program: SerializedProgram = when {
        path.endsWith(".vel") -> VeloCompiler(natives).compile(path) ?: return
        path.endsWith(".vbc") -> {
            val file = File(path)
            Bytecode.read(file).also { p ->
                println("Bytecode read: ${p.frames.size} frames, ${p.natives.size} native refs / ${file.length()} bytes")
            }
        }
        else -> {
            println("Unsupported file type: $path")
            return
        }
    }

    if (out != null) {
        val file = File(out)
        Bytecode.write(program, file)
        println("Bytecode written: ${file.length()} bytes")
    } else {
        println()
        VeloRuntime(natives).run(program)
    }
}
