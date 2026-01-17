package compiler.parser.parselets.operators

import compiler.nodes.AddressOfNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class AddressOfParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Parse the target expression, applying postfix operators
        val target = parser.parseExpression(Precedence.UNARY)
        return AddressOfNode(target)
    }
}
