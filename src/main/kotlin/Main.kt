import compiler.Context
import compiler.Heap
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
    vm2()
    if (true) return

//    runVM("/home/solkin/Projects/Backend/false-vm/fib.fbc")
//    if (true) return

    val prog = Parser::class.java.getResource("/fibonacci-recursive.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parse in $elapsed ms")

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

    time = System.currentTimeMillis()
    val result = node.evaluate(globalEnv)

    elapsed = System.currentTimeMillis() - time
    println("\nRun in $elapsed ms")
}

fun vm2() {
    val prog = Parser::class.java.getResource("/test.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    val node = parser.parse()
    val globalEnv = createGlobalEnvironment<Value<*>>().apply {
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
    try {
        node.evaluate(globalEnv)
    } catch (ex: Throwable) {
        println("!! interpreter halted with an exception: ${ex.message}")
    }

    val ctx = Context(ops = ArrayList(), heap = Heap())
    try {
        node.compile(ctx)
    } catch (ex: Throwable) {
        println("!! compilation failed")
        println(ex.message)
        return
    }

    val vm = VM()
    vm.load(SimpleParser(ctx.operations()))
    vm.run()
}
