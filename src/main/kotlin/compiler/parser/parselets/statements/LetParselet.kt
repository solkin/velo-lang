package compiler.parser.parselets.statements

import compiler.nodes.CallNode
import compiler.nodes.DefNode
import compiler.nodes.FuncNode
import compiler.nodes.LetNode
import compiler.nodes.Node
import compiler.nodes.ScopeNode
import compiler.nodes.VoidNode
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class LetParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        if (parser.match(TokenType.VARIABLE)) {
            val name = parser.consume(TokenType.VARIABLE).value as? String
            val defsNodes = parser.parseDelimited('(', ')', ',') {
                TypeParser.parseDef(parser)
            }
            val defs = defsNodes.map { it as DefNode }
            val type = TypeParser.parseDefType(parser)
            val body = parser.parseExpression()
            return CallNode(
                func = FuncNode(
                    name = name,
                    native = false,
                    defs = defs,
                    type = type,
                    body = body
                ),
                args = defs.map { def -> def.def ?: VoidNode }
            )
        }
        val varsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val vars = varsNodes.map { it as DefNode }
        return ScopeNode(
            LetNode(
                vars = vars,
                body = parser.parseExpression()
            )
        )
    }
}
