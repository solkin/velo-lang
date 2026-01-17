package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.nodes.ScopeNode
import compiler.nodes.WhileNode
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class WhileParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val cond = parser.parseExpression(allowApply = false)
        val body = parser.parseDelimited('{', '}', ';') {
            parser.parseExpression()
        }
        val bodyNode = when (body.size) {
            0 -> compiler.nodes.VoidNode
            1 -> body[0]
            else -> compiler.nodes.ProgramNode(prog = body)
        }
        return WhileNode(
            cond = cond,
            expr = ScopeNode(bodyNode),
        )
    }
}
