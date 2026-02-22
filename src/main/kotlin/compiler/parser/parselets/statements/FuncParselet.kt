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

class FuncParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val name = if (parser.match(TokenType.VARIABLE)) {
            parser.consume(TokenType.VARIABLE).value as? String
        } else {
            null
        }
        val typeParams = parseTypeParams(parser)
        val savedGenerics = parser.context.saveGenericTypes()
        typeParams.forEach { parser.context.registerGenericType(it) }
        val defsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val defs = defsNodes.map { it as DefNode }
        val type = TypeParser.parseDefType(parser)
        val body = if (parser.match(TokenType.KEYWORD, "native")) {
            parser.consume(TokenType.KEYWORD, "native")
            VoidNode
        } else {
            val bodyList = parser.parseDelimited('{', '}', ';') {
                parser.parseExpression()
            }
            when (bodyList.size) {
                0 -> VoidNode
                1 -> bodyList[0]
                else -> ProgramNode(prog = bodyList)
            }
        }
        parser.context.restoreGenericTypes(savedGenerics)
        return FuncNode(
            name = name,
            native = false,
            typeParams = typeParams,
            defs = defs,
            type = type,
            body = body
        )
    }
}
