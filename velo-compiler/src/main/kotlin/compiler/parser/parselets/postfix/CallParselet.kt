package compiler.parser.parselets.postfix

import compiler.nodes.CallNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.Token

class CallParselet : InfixParselet {
    override val precedence = Precedence.CALL

    override fun parse(parser: ExpressionParser, left: Node, token: Token): Node {
        // Token '(' is already consumed by PrattParser, parse args until ')'
        val args = mutableListOf<Node>()
        var first = true
        while (!parser.eof()) {
            if (parser.match(compiler.parser.TokenType.PUNCTUATION, ')')) {
                break
            }
            if (first) {
                first = false
            } else {
                parser.consume(compiler.parser.TokenType.PUNCTUATION, ',')
            }
            if (parser.match(compiler.parser.TokenType.PUNCTUATION, ')')) {
                break
            }
            args.add(parser.parseExpression())
        }
        parser.consume(compiler.parser.TokenType.PUNCTUATION, ')')
        return CallNode(func = left, args = args)
    }
}
