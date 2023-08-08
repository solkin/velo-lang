import nodes.*
import parser.*

fun main(args: Array<String>) {
    val prog = Parser::class.java.getResource("/sample1.vel").readText()

    val input = StringInput(prog)
    val stream = TokenStream(input)
    val parser = Parser(stream)

    val node = parser.parse()

    val globalEnv = createGlobalEnvironment<Type<*>>().apply {
        def(
            "len",
            LambdaType(
                fun(args: List<Type<*>>, it: Type<*>?): Type<*> {
                    return when (val arg = args.takeIf { it.isNotEmpty() }?.get(0)) {
                        is ListType -> NumType(arg.value.size.toDouble())
                        is StrType -> NumType(arg.value.length.toDouble())
                        else -> NumType(0.toDouble())
                    }
                }
            )
        )
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

    val time = System.currentTimeMillis()
    val result = node.evaluate(globalEnv)

    val elapsed = System.currentTimeMillis() - time
    println("\n\nRun in $elapsed ms")
}
