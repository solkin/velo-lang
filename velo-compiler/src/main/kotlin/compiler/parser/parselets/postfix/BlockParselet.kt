package compiler.parser.parselets.postfix

import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class BlockParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Token '{' is already consumed
        val prog = mutableListOf<Node>()
        var first = true
        while (!parser.eof()) {
            if (parser.match(compiler.parser.TokenType.PUNCTUATION, '}')) {
                break
            }
            if (first) {
                first = false
            } else {
                parser.consume(compiler.parser.TokenType.PUNCTUATION, ';')
            }
            if (parser.match(compiler.parser.TokenType.PUNCTUATION, '}')) {
                break
            }
            prog.add(parser.parseExpression())
        }
        parser.consume(compiler.parser.TokenType.PUNCTUATION, '}')
        return when (prog.size) {
            0 -> VoidNode
            1 -> prog[0]
            else -> ProgramNode(prog = prog)
        }
    }
}
