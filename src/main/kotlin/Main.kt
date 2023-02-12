import interpreter.Interpreter
import interpreter.createGlobalEnvironment
import parser.*

fun main(args: Array<String>) {
    val prog = "println(\"Hello World!\");"

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    val node = parser.parse()

    val globalEnv = createGlobalEnvironment().apply {
        def("print", RunnableNode(
            func = fun (args: List<Node>) : Node {
                print(args.toString())
                return FALSE
            }
        ))
        def("println", RunnableNode(
            func = fun (args: List<Node>) : Node {
                println(args.toString())
                return FALSE
            }
        ))
    }
    val interpreter = Interpreter()

    val result = interpreter.evaluate(node, globalEnv)

    println(result)
}