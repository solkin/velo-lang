package compiler.parser.parselets.literals

import compiler.nodes.BoolNode
import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class BoolParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        return BoolNode(token.value == "true")
    }
}
