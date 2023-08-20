import nodes.*
import parser.Parser
import parser.StringInput
import parser.TokenStream
import java.awt.Container
import javax.swing.JButton
import javax.swing.JFrame


fun main(args: Array<String>) {
    val prog = Parser::class.java.getResource("/sample1.vel").readText()

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
