import nodes.BoolValue
import nodes.FuncValue
import nodes.StrValue
import nodes.Value
import parser.Parser
import parser.StringInput
import parser.TokenStream
import vm.VM
import vm2.SimpleParser
import vm2.VM2
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException

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
                    return StrValue(readlnOrNull().orEmpty())
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
    val prog = Parser::class.java.getResource("/primes.vel").readText()

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

    val ctx = CompilerContext(ops = ArrayList(), vars = HashMap())
    try {
        node.compile(ctx)
    } catch (ex: Throwable) {
        println("!! compilation failed")
        println(ex.message)
    }

    val vm2 = VM2()
    vm2.load(SimpleParser(ctx.operations()))
    vm2.run()
}

private fun runVM(path: String) {
    val f = File(path)
    val img = IntArray((f.length() / 4).toInt())
    var c = 0
    FileInputStream(f).use { inp ->
        try {
            while (true) {
                val ch1: Int = inp.read()
                val ch2: Int = inp.read()
                val ch3: Int = inp.read()
                val ch4: Int = inp.read()
                if ((ch1 or ch2 or ch3 or ch4) < 0) throw EOFException()
                val i = ((ch1 shl 0) + (ch2 shl 8) + (ch3 shl 16) + (ch4 shl 24))
                img[c++] = i
            }
        } catch (ignored: IOException) {
        }
    }
    val vm = VM(102400, 512, 512)
    vm.load(img)
    val time1 = System.currentTimeMillis()
    vm.run()
    val elapsed1 = System.currentTimeMillis() - time1
    println("\nRun in $elapsed1 ms")
}

inline infix fun Byte.shl(other: Byte): Byte = (this.toInt() shl other.toInt()).toByte()
