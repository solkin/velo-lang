package compiler.parser.parselets.operators

import compiler.nodes.Node
import compiler.nodes.UnaryNode
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class UnaryParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val operand = parser.parseExpression(Precedence.UNARY)
        return UnaryNode("-", operand)
    }
}
