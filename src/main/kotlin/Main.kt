import compiler.Context
import compiler.Enumerator
import compiler.parser.Parser
import compiler.parser.StringInput
import compiler.parser.TokenStream
import vm.SimpleParser
import vm.VM
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("File path is required")
        return
    }
    val path = args[0]
    val prog = if (path.startsWith("res://")) {
        Parser::class.java.getResource(path.substring(5))?.readText() ?: return
    } else if (path.startsWith("file://")) {
        File(path.substring(6)).readText()
    } else {
        return
    }

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parsed in $elapsed ms")

    val ctx = Context(ops = ArrayList(), enumerator = Enumerator())
    try {
        time = System.currentTimeMillis()
        node.compile(ctx)
        elapsed = System.currentTimeMillis() - time
        println("Compiled in $elapsed ms")
    } catch (ex: Throwable) {
        println("!! Compilation failed: ${ex.message}")
        return
    }
    println()

    val vm = VM()
    vm.load(SimpleParser(ctx.operations()))
    vm.run()
}
