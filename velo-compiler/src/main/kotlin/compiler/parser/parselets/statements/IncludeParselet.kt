package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class IncludeParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val path = parser.peek()?.takeIf { tok ->
            tok.type == TokenType.STRING
        } ?: throw IllegalStateException("Include keyword must end with a relative file path")
        parser.consume(TokenType.STRING)
        parser.consume(TokenType.PUNCTUATION, ';')
        parser.depLoader.load(name = path.value as String)
        // After include, parse the next expression
        return parser.parseExpression()
    }
}
