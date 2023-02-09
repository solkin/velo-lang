import parser.Parser
import parser.StringInput
import parser.TokenStream

fun main(args: Array<String>) {
    val prog = "println(\"Hello World!\");"

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    val node = parser.parse()

    println(node)
}