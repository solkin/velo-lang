import compiler.Context
import compiler.Scope
import compiler.createScope
import compiler.parser.Parser
import compiler.parser.StringInput
import compiler.parser.TokenStream
import utils.BytecodeInputStream
import utils.BytecodeOutputStream
import vm.Operation
import vm.SimpleParser
import vm.VM
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

    val ops = if (path.endsWith(".vel")) {
        val prog = if (path.startsWith("res://")) {
            Parser::class.java.getResource(path.substring(5))?.readText() ?: return
        } else if (path.startsWith("file://")) {
            File(path.substring(6)).readText()
        } else {
            println("Unsupported input scheme")
            return
        }
        compile(prog)
    } else if (path.endsWith(".vbc")) {
        var ops: List<Operation>?
        val file = File(path)
        FileInputStream(file).use { fis ->
            DataInputStream(fis).use { dis ->
                BytecodeInputStream(dis).use { bis ->
                    ops = bis.readOperations().also { ops ->
                        println("Bytecode read: ${ops.size} operations / ${file.length()} bytes")
                    }
                }
            }
        }
        ops
    } else {
        println("Unsupported file type")
        return
    }

    if (ops != null) {
        if (bc != null) {
            val file = File(bc)
            FileOutputStream(file).use { fos ->
                DataOutputStream(fos).use { dos ->
                    BytecodeOutputStream(dos).use { bos ->
                        bos.write(ops)
                        bos.flush()
                    }
                }
            }
            println("Bytecode written: ${file.length()} bytes")
        } else {
            println()
            runVM(ops)
        }
    }
}

fun compile(prog: String): List<Operation>? {
    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parsed in $elapsed ms")

    val ctx = Context(ops = ArrayList(), scope = createScope())
    try {
        time = System.currentTimeMillis()
        node.compile(ctx)
        elapsed = System.currentTimeMillis() - time
        println("Compiled in $elapsed ms [${ctx.size()} ops]")
        return ctx.operations()
    } catch (ex: Throwable) {
        println("!! Compilation failed: ${ex.message}")
    }
    println()
    return null
}

fun runVM(ops: List<Operation>) {
    val vm = VM()
    vm.load(SimpleParser(ops))
    vm.run()
}
