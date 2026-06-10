package compiler.parser.parselets.postfix

import compiler.nodes.Node
import compiler.nodes.PropNode
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.Token
import compiler.parser.TokenType

class PropertyParselet : InfixParselet {
    override val precedence = Precedence.PROPERTY

    override fun parse(parser: ExpressionParser, left: Node, token: Token): Node {
        val tok = parser.peek()
        if (tok?.type == TokenType.VARIABLE || tok?.type == TokenType.KEYWORD || tok?.type == TokenType.NUMBER) {
            val tokVal = parser.consume(tok.type).value
            val name = when (tokVal) {
                is String -> tokVal
                is Int -> tokVal.toString()
                else -> {
                    parser.peek()?.let { parser.consume(it.type) }
                    throw IllegalArgumentException("Invalid property type $tokVal")
                }
            }
            if (name.isEmpty()) {
                parser.peek()?.let { parser.consume(it.type) }
                throw IllegalArgumentException("Property can not be empty")
            }
            val args: List<Node>? = if (parser.match(TokenType.PUNCTUATION, '(')) {
                parser.parseDelimited('(', ')', ',') {
                    parser.parseExpression()
                }
            } else {
                null
            }
            return PropNode(name = name, args = args, parent = left)
        }
        parser.peek()?.let { parser.consume(it.type) }
        throw IllegalArgumentException("Invalid property syntax")
    }
}
