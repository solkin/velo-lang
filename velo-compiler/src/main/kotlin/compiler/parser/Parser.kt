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
        var statements = parseStatements(pratt)
        // dict[K:V] is sugar over the stdlib Map class: when the program used
        // dict syntax and did not import the implementation itself, pull it in
        // (parsed in isolation) and compile it ahead of the user code.
        statements = maybePrepend(SourceLoader.STDLIB_MAP, context.dictUsed, statements)
        return ProgramNode(prog = statements)
    }

    /** Parse an auto-imported stdlib [module] in isolation and prepend it, once. */
    private fun maybePrepend(module: String, used: Boolean, statements: List<Node>): List<Node> {
        if (!used || depLoader.isLoaded(module)) return statements
        val loaded = depLoader.module(module, fromDir = null) ?: return statements
        val sub = PrattParser(TokenStream(StringInput(loaded.text)), context, depLoader, currentDir = loaded.dir)
            .also { VeloGrammar.configure(it) }
        return parseStatements(sub) + statements
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
