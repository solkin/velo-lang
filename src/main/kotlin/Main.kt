import nodes.BoolType
import nodes.FuncType
import nodes.StrType
import nodes.Type
import parser.Parser
import parser.StringInput
import parser.TokenStream
import vm.VM
import vm2.SimpleParser
import vm2.VM2
import vm2.operations.*
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException

fun main(args: Array<String>) {
    val vm2 = VM2()
    vm2.load(
        SimpleParser(
            operations = listOf(
                Push(value = 2),
                Def(index = 1),

                Push(value = 2),
                Get(index = 1),
                Plus(),

                Get(index = 1),
                Plus(),

                Set(index = 1),

                Push("Value is: "),
                Get(index = 1),
                Print(),
                Println(),

                Push(value = 16),
                Get(index = 1),
                Less(),
                If(addr = 2),

                Push("Done"),
                Println(),
            )
        )
    )
    vm2.run()
    if (true) return

    runVM("/home/solkin/Projects/Backend/false-vm/sample.fbc")
    if (true) return


    val prog = Parser::class.java.getResource("/diamond.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parse in $elapsed ms")

    val globalEnv = createGlobalEnvironment<Type<*>>().apply {
        def(
            "readLine",
            FuncType(
                fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                    return StrType(readlnOrNull().orEmpty())
                }
            )
        )
        def(
            "print",
            FuncType(
                fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                    args.forEach { print(it.value()) }
                    return BoolType(false)
                }
            )
        )
        def(
            "println",
            FuncType(
                fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                    args.takeIf { it.isNotEmpty() }?.forEach { println(it.value()) } ?: println()
                    return BoolType(false)
                }
            )
        )
    }

    time = System.currentTimeMillis()
    val result = node.evaluate(globalEnv)

    elapsed = System.currentTimeMillis() - time
    println("\nRun in $elapsed ms")
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
    if (true) return
}

inline infix fun Byte.shl(other: Byte): Byte = (this.toInt() shl other.toInt()).toByte()
