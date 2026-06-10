package compiler.parser.parselets.operators

import compiler.nodes.AssignNode
import compiler.nodes.BinaryNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.Token

class AssignParselet : InfixParselet {
    override val precedence = Precedence.ASSIGNMENT
    override val isRightAssociative = true

    override fun parse(parser: ExpressionParser, left: Node, token: Token): Node {
        val operator = token.value as String
        val right = parser.parseExpression(precedence - 1) // Right-associative
        
        return when (operator) {
            "=" -> AssignNode(left = left, right = right)
            // Compound assignment operators: x += y -> x = x + y
            "+=" -> AssignNode(
                left = left,
                right = BinaryNode(operator = "+", left = left, right = right)
            )
            "-=" -> AssignNode(
                left = left,
                right = BinaryNode(operator = "-", left = left, right = right)
            )
            "*=" -> AssignNode(
                left = left,
                right = BinaryNode(operator = "*", left = left, right = right)
            )
            "/=" -> AssignNode(
                left = left,
                right = BinaryNode(operator = "/", left = left, right = right)
            )
            "%=" -> AssignNode(
                left = left,
                right = BinaryNode(operator = "%", left = left, right = right)
            )
            else -> throw IllegalArgumentException("Unknown assignment operator: $operator")
        }
    }
}
