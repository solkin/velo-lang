import parser.*

fun main(args: Array<String>) {
    val prog = Parser::class.java.getResource("/sample1.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    val node = parser.parse()

    val globalEnv = createGlobalEnvironment<Any>().apply {
        def("print", fun(args: List<Any>): Any {
            print(args)
            return false
        })
        def("println", fun(args: List<Any>): Any {
            println(args)
            return false
        })
    }

    val time = System.currentTimeMillis()
    val result = node.evaluate(globalEnv)

    val elapsed = System.currentTimeMillis() - time
    println("\n\nRun in $elapsed ms")
}