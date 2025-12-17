import utils.BytecodeInputStream
import utils.BytecodeOutputStream
import utils.SerializedFrame
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

    val frames = if (path.endsWith(".vel")) {
        runtime.compile(path)
    } else if (path.endsWith(".vbc")) {
        var frames: List<SerializedFrame>?
        val file = File(path)
        FileInputStream(file).use { fis ->
            DataInputStream(fis).use { dis ->
                BytecodeInputStream(dis).use { bis ->
                    frames = bis.readFrames().also { ops ->
                        println("Bytecode read: ${ops.size} frames / ${file.length()} bytes")
                    }
                }
            }
        }
        frames
    } else {
        println("Unsupported file type")
        return
    }

    if (frames != null) {
        if (bc != null) {
            val file = File(bc)
            FileOutputStream(file).use { fos ->
                DataOutputStream(fos).use { dos ->
                    BytecodeOutputStream(dos).use { bos ->
                        bos.write(frames)
                        bos.flush()
                    }
                }
            }
            println("Bytecode written: ${file.length()} bytes")
        } else {
            println()
            runtime.run(frames)
        }
    }
}
