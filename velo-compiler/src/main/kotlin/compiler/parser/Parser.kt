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
        // Prepend every stdlib module requested during parsing (e.g. dict[K:V]
        // lowers onto std/map), each parsed in isolation and compiled ahead of
        // the user code, unless the program imported it explicitly. A prepended
        // module may itself request more, so drain until the set is exhausted.
        val prepended = mutableSetOf<String>()
        while (true) {
            val pending = context.autoImports.filterNot { it in prepended }
            if (pending.isEmpty()) break
            for (module in pending) {
                prepended.add(module)
                statements = prependModule(module, statements)
            }
        }
        return ProgramNode(prog = statements)
    }

    /** Parse an auto-imported stdlib [module] in isolation and prepend it, once. */
    private fun prependModule(module: String, statements: List<Node>): List<Node> {
        if (depLoader.isLoaded(module)) return statements
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
