package compiler.parser.parselets.statements

import compiler.nodes.DefNode
import compiler.nodes.FuncNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class ExtParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val selfNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val self = selfNodes.map { it as DefNode }
        if (self.size != 1) {
            parser.peek()?.let { parser.consume(it.type) }
            throw IllegalArgumentException("Self type and variable must be defined for extension function")
        }
        val name = parser.peek()?.takeIf { tok ->
            tok.type == TokenType.VARIABLE || tok.type == TokenType.KEYWORD
        }?.let { parser.consume(it.type).value as? String }
            ?: throw IllegalArgumentException("Expected function name")
        val defsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val defs = defsNodes.map { it as DefNode }
        val type = TypeParser.parseDefType(parser)
        val bodyList = parser.parseDelimited('{', '}', ';') {
            parser.parseExpression()
        }
        val body = when (bodyList.size) {
            0 -> VoidNode
            1 -> bodyList[0]
            else -> ProgramNode(prog = bodyList)
        }
        return FuncNode(
            name = self.first().type.name() + "@" + (name ?: ""),
            native = false,
            defs = self + defs,
            type = type,
            body = body
        )
    }
}
