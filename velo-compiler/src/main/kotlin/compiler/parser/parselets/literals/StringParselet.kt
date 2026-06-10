package compiler.parser.parselets.literals

import compiler.nodes.Node
import compiler.nodes.StringNode
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class StringParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        return StringNode(token.value as String)
    }
}
