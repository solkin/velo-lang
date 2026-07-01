package compiler.parser

import compiler.nodes.Node
import compiler.nodes.ProgramNode
import core.NativeRegistry

class Parser(
    private val stream: TokenStream,
    private val depLoader: DependencyLoader,
    nativeRegistry: NativeRegistry? = null,
    rootDir: java.io.File? = null,
) {
    private val context = ParserContext(nativeRegistry)
    private val pratt = PrattParser(stream, context, depLoader, currentDir = rootDir).also {
        VeloGrammar.configure(it)
    }

    fun parse(): Node {
        val statements = parseStatements(pratt)
        // dict[K:V] is sugar over the stdlib Map class: when the program used
        // dict syntax and did not import the implementation itself, pull it in
        // (parsed in isolation) and compile it ahead of the user code.
        if (context.dictUsed && !depLoader.isLoaded(SourceLoader.STDLIB_MAP)) {
            val module = depLoader.module(SourceLoader.STDLIB_MAP, fromDir = null)
            if (module != null) {
                val sub = PrattParser(TokenStream(StringInput(module.text)), context, depLoader, currentDir = module.dir)
                    .also { VeloGrammar.configure(it) }
                return ProgramNode(prog = parseStatements(sub) + statements)
            }
        }
        return ProgramNode(prog = statements)
    }

    private fun parseStatements(p: PrattParser): List<Node> {
        val statements = mutableListOf<Node>()
        while (!p.eof()) {
            statements.add(p.parseExpression())
            if (!p.eof()) {
                p.consume(TokenType.PUNCTUATION, ';')
            }
        }
        return statements
    }
}
