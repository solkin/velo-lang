package compiler.parser.parselets.literals

import compiler.nodes.Node
import compiler.nodes.VarNode
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class VariableParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        return VarNode(token.value as String)
    }
}
