package compiler.parser.parselets.postfix

import compiler.nodes.IndexNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.Token

class IndexParselet : InfixParselet {
    override val precedence = Precedence.INDEX

    override fun parse(parser: ExpressionParser, left: Node, token: Token): Node {
        // Token '[' is already consumed by PrattParser
        val index = parser.parseExpression()
        parser.consume(compiler.parser.TokenType.PUNCTUATION, ']')
        return IndexNode(list = left, index = index)
    }
}
