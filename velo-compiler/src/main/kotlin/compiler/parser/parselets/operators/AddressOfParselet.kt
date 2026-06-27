package compiler.parser.parselets.operators

import compiler.nodes.AddressOfNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class AddressOfParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Parse the target expression, applying postfix operators. The operand
        // binds below the postfix ops (call/index/property, precedence INDEX) so
        // they attach to it — `&a[i]` is `&(a[i])`, not `(&a)[i]` — but above the
        // binary operators, so `&a + b` stays `(&a) + b`.
        val target = parser.parseExpression(Precedence.MULTIPLICATIVE)
        return AddressOfNode(target)
    }
}
