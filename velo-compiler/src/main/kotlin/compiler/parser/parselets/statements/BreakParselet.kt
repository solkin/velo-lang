package compiler.parser.parselets.statements

import compiler.nodes.BreakNode
import compiler.nodes.ContinueNode
import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class BreakParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node = BreakNode
}

class ContinueParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node = ContinueNode
}
