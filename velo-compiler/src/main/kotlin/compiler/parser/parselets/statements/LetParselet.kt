package compiler.parser.parselets.statements

import compiler.nodes.LetNode
import compiler.nodes.Node
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

/** `let <name> = <expr>` — immutable local with an inferred type. */
class LetParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val name = parser.consume(TokenType.VARIABLE).value as String
        parser.consume(TokenType.OPERATOR, "=")
        val value = parser.parseExpression()
        return LetNode(name = name, value = value)
    }
}
