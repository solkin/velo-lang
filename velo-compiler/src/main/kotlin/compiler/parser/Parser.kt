package compiler.parser

import compiler.nodes.Node
import compiler.nodes.ProgramNode
import core.NativeRegistry

class Parser(
    private val stream: TokenStream,
    private val depLoader: DependencyLoader,
    nativeRegistry: NativeRegistry? = null,
) {
    private val context = ParserContext(nativeRegistry)
    private val pratt = PrattParser(stream, context, depLoader).also {
        VeloGrammar.configure(it)
    }

    fun parse(): Node {
        val statements = parseStatements()
        // dict[K:V] is sugar over the stdlib Map class: when the program
        // used dict syntax and did not include the implementation itself,
        // pull it in and compile it ahead of the user code.
        if (context.dictUsed && !depLoader.isLoaded(SourceLoader.STDLIB_MAP)) {
            depLoader.load(SourceLoader.STDLIB_MAP)
            val stdlib = parseStatements()
            return ProgramNode(prog = stdlib + statements)
        }
        return ProgramNode(prog = statements)
    }

    private fun parseStatements(): List<Node> {
        val statements = mutableListOf<Node>()
        while (!pratt.eof()) {
            statements.add(pratt.parseExpression())
            if (!pratt.eof()) {
                pratt.consume(TokenType.PUNCTUATION, ';')
            }
        }
        return statements
    }
}
