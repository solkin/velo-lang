import compiler.CompilerFrame
import compiler.Context
import compiler.parser.FileInput
import compiler.parser.Parser
import compiler.parser.TokenStream
import utils.BytecodeInputStream
import utils.BytecodeOutputStream
import utils.SerializedFrame
import vm.SimpleParser
import vm.VM
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("File path is required")
        return
    }
    val path = args[0]
    val bc = args.getOrNull(1)

    val frames = if (path.endsWith(".vel")) {
        val file = File(path)
        compile(input = FileInput(dir = file.parent).apply {
            load(path = file.name)
        })
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
            runVM(frames)
        }
    }
}

fun compile(input: FileInput): List<SerializedFrame>? {
    val stream = TokenStream(input)
    val parser = Parser(stream, depLoader = input)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parsed in $elapsed ms")

    val ctx = Context(
        parent = null,
        frame = CompilerFrame(num = 0, ops = mutableListOf(), vars = mutableMapOf(), varCounter = AtomicInteger()),
        frameCounter = AtomicInteger(),
    )
    try {
        time = System.currentTimeMillis()
        node.compile(ctx)
        elapsed = System.currentTimeMillis() - time
        println("Compiled in $elapsed ms [${ctx.frames().size} frames]")
        return ctx.frames().map {
            SerializedFrame(
                num = it.num,
                ops = it.ops,
                vars = it.vars.map { i -> i.value.index }
            )
        }
    } catch (ex: Throwable) {
        println("!! Compilation failed: ${ex.message}")
    }
    println()
    return null
}

fun runVM(frames: List<SerializedFrame>) {
    val vm = VM()
    vm.load(SimpleParser(frames))
    vm.run()
}
