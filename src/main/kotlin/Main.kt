import nodes.*
import parser.*

fun main(args: Array<String>) {
    val prog = Parser::class.java.getResource("/sample3.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    var time = System.currentTimeMillis()
    val node = parser.parse()
    var elapsed = System.currentTimeMillis() - time
    println("Parse in $elapsed ms")

    val globalEnv = createGlobalEnvironment<Type<*>>().apply {
        def(
            "print",
            LambdaType(
                fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                    args.forEach { print(it.value()) }
                    return BoolType(false)
                }
            )
        )
        def(
            "println",
            LambdaType(
                fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                    args.forEach { println(it.value()) }
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
