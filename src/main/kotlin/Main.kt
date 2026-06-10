import utils.BytecodeInputStream
import utils.BytecodeOutputStream
import utils.SerializedProgram
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("File path is required")
        return
    }
    val path = args[0]
    val bc = args.getOrNull(1)

    // Create runtime with default native classes registered
    val runtime = VeloRuntime()

    val program = if (path.endsWith(".vel")) {
        runtime.compile(path)
    } else if (path.endsWith(".vbc")) {
        var program: SerializedProgram?
        val file = File(path)
        FileInputStream(file).use { fis ->
            DataInputStream(fis).use { dis ->
                BytecodeInputStream(dis).use { bis ->
                    program = bis.readProgram().also { p ->
                        println("Bytecode read: ${p.frames.size} frames, ${p.natives.size} native refs / ${file.length()} bytes")
                    }
                }
            }
        }
        program
    } else {
        println("Unsupported file type")
        return
    }

    if (program != null) {
        if (bc != null) {
            val file = File(bc)
            FileOutputStream(file).use { fos ->
                DataOutputStream(fos).use { dos ->
                    BytecodeOutputStream(dos).use { bos ->
                        bos.write(program)
                        bos.flush()
                    }
                }
            }
            println("Bytecode written: ${file.length()} bytes")
        } else {
            println()
            runtime.run(program)
        }
    }
}
