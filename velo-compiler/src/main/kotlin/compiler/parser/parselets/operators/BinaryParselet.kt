package compiler.parser.parselets.operators

import compiler.nodes.BinaryNode
import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.Token

class BinaryParselet(override val precedence: Int) : InfixParselet {
    override fun parse(parser: ExpressionParser, left: Node, token: Token): Node {
        val right = parser.parseExpression(precedence)
        return BinaryNode(
            operator = token.value as String,
            left = left,
            right = right
        )
    }
}
