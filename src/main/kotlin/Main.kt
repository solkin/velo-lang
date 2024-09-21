import compiler.Context
import compiler.Enumerator
import compiler.createGlobalEnvironment
import compiler.nodes.BoolValue
import compiler.nodes.FuncValue
import compiler.nodes.StringValue
import compiler.nodes.Value
import compiler.parser.Parser
import compiler.parser.StringInput
import compiler.parser.TokenStream
import vm.SimpleParser
import vm.VM

fun main(args: Array<String>) {
    val prog = Parser::class.java.getResource("/fibonacci.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parsed in $elapsed ms")

    val globalEnv = createGlobalEnvironment<Value<*>>().apply {
        def(
            "readLine",
            FuncValue(
                fun(args: List<Value<*>>, it: Value<*>?): Value<*> {
                    return StringValue(readlnOrNull().orEmpty())
                }
            )
        )
        def(
            "print",
            FuncValue(
                fun(args: List<Value<*>>, it: Value<*>?): Value<*> {
                    args.forEach { print(it.value()) }
                    return BoolValue(false)
                }
            )
        )
        def(
            "println",
            FuncValue(
                fun(args: List<Value<*>>, it: Value<*>?): Value<*> {
                    args.takeIf { it.isNotEmpty() }?.forEach { println(it.value()) } ?: println()
                    return BoolValue(false)
                }
            )
        )
    }
    println()

    time = System.currentTimeMillis()
    try {
        node.evaluate(globalEnv)
    } catch (ex: Throwable) {
        println("!! Interpreter halted with an exception: ${ex.message}")
    }

    elapsed = System.currentTimeMillis() - time
    println("\nRun in $elapsed ms")

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
