package compiler.parser

import compiler.nodes.Node
import compiler.nodes.ProgramNode

class Parser(private val stream: TokenStream, private val depLoader: DependencyLoader) {
    private val context = ParserContext()
    private val pratt = PrattParser(stream, context, depLoader).also {
        VeloGrammar.configure(it)
    }

    fun parse(): Node {
        val statements = mutableListOf<Node>()
        while (!pratt.eof()) {
            statements.add(pratt.parseExpression())
            if (!pratt.eof()) {
                pratt.consume(TokenType.PUNCTUATION, ';')
            }
        }
        return ProgramNode(prog = statements)
    }
}
