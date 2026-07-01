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
            // In a namespaced module (`import "x" as ns`) a top-level name is
            // mangled to `ns$name`, reached from outside as `ns.name`.
            (parser.consume(TokenType.VARIABLE).value as? String)?.let { parser.context.declareName(it) }
        } else {
            null
        }
        val typeParamList = parseTypeParams(parser)
        val typeParams = typeParamList.map { it.name }
        val savedGenerics = parser.context.saveGenericTypes()
        typeParamList.forEach { parser.context.registerGenericType(it.name, it.bound) }
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
        parser.context.restoreGenericTypes(savedGenerics)
        return FuncNode(
            name = name,
            typeParams = typeParams,
            defs = defs,
            type = type,
            body = body
        )
    }
}
