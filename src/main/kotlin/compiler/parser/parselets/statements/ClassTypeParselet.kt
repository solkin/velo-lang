package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class ClassTypeParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val className = token.value as String
        val classType = parser.context.getClassType(className)
            ?: throw IllegalArgumentException("Unknown class type: $className")
        val resolvedType = if (classType.typeParams.isNotEmpty()
            && parser.match(TokenType.PUNCTUATION, '[')
        ) {
            val typeArgs = TypeParser.parseDerivedTypes(parser, count = classType.typeParams.size)
            classType.copy(typeArgs = typeArgs)
        } else {
            classType
        }
        return TypeParser.parseDefBody(parser, resolvedType)
    }
}
