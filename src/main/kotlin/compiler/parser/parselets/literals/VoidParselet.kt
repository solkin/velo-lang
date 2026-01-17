package compiler.parser.parselets.literals

import compiler.nodes.Node
import compiler.nodes.VoidNode
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class VoidParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        return VoidNode
    }
}
