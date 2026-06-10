package compiler.parser.parselets.postfix

import compiler.nodes.ApplyNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.Token

class ApplyParselet : InfixParselet {
    override val precedence = Precedence.CALL

    override fun parse(parser: ExpressionParser, left: Node, token: Token): Node {
        // Token '{' is already consumed by PrattParser
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
        val body = when (prog.size) {
            0 -> VoidNode
            1 -> prog[0]
            else -> ProgramNode(prog = prog)
        }
        return ApplyNode(target = left, body = body)
    }
}
