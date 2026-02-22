package compiler.parser.parselets.statements

import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DefNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

class ClassParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val native = token.value == "native"
        val className = TypeParser.parseVarname(parser)
        val typeParams = parseTypeParams(parser)
        val savedGenerics = parser.context.saveGenericTypes()
        typeParams.forEach { parser.context.registerGenericType(it) }
        parser.context.registerClass(className, ClassType(name = className, typeParams = typeParams))
        val defsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val defs = defsNodes.map { it as DefNode }
        val bodyList = parser.parseDelimited('{', '}', ';') {
            parser.parseExpression()
        }
        val body = when (bodyList.size) {
            0 -> VoidNode
            1 -> bodyList[0]
            else -> ProgramNode(prog = bodyList)
        }
        parser.context.restoreGenericTypes(savedGenerics)
        return ClassNode(
            name = className,
            native = native,
            typeParams = typeParams,
            defs = defs,
            body = body
        )
    }
}

fun parseTypeParams(parser: ExpressionParser): List<String> {
    if (!parser.match(TokenType.PUNCTUATION, '[')) return emptyList()
    val params = mutableListOf<String>()
    parser.consume(TokenType.PUNCTUATION, '[')
    var first = true
    while (!parser.eof()) {
        if (parser.match(TokenType.PUNCTUATION, ']')) break
        if (first) first = false else parser.consume(TokenType.PUNCTUATION, ',')
        if (parser.match(TokenType.PUNCTUATION, ']')) break
        params.add(TypeParser.parseVarname(parser))
    }
    parser.consume(TokenType.PUNCTUATION, ']')
    return params
}
