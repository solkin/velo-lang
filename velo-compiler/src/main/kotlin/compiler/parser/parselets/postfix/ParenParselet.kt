package compiler.parser.parselets.postfix

import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class ParenParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Token '(' is already consumed, just parse expression and consume ')'
        val expr = parser.parseExpression()
        parser.consume(compiler.parser.TokenType.PUNCTUATION, ')')
        return expr
    }
}
