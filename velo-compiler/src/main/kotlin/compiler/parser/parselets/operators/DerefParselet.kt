package compiler.parser.parselets.operators

import compiler.nodes.DerefNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class DerefParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Parse the target expression, applying postfix operators. Like
        // address-of, the operand binds below the postfix ops so `*p[i]` is
        // `*(p[i])`, but above the binary operators so `*p + 1` is `(*p) + 1`.
        val target = parser.parseExpression(Precedence.MULTIPLICATIVE)
        return DerefNode(target)
    }
}
