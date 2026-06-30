package compiler.parser.parselets.statements

import compiler.nodes.ForEachNode
import compiler.nodes.ForRangeNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

/**
 * `for <name> in <start> .. <end> { body }`   — integer range, end exclusive
 * `for <name> in <array> { body }`            — iterate an array's elements
 */
class ForParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val name = parser.consume(TokenType.VARIABLE).value as String
        parser.consume(TokenType.KEYWORD, "in")

        // `allowApply = false` so the body's `{` is not taken as an apply.
        val first = parser.parseExpression(allowApply = false)
        val isRange = parser.match(TokenType.OPERATOR, "..")
        val end = if (isRange) {
            parser.consume(TokenType.OPERATOR, "..")
            parser.parseExpression(allowApply = false)
        } else {
            null
        }

        val bodyNodes = parser.parseDelimited('{', '}', ';') {
            parser.parseExpression()
        }
        val body = when (bodyNodes.size) {
            0 -> VoidNode
            1 -> bodyNodes[0]
            else -> ProgramNode(prog = bodyNodes)
        }

        return if (end != null) {
            ForRangeNode(name = name, start = first, end = end, body = body)
        } else {
            ForEachNode(name = name, collection = first, body = body)
        }
    }
}
