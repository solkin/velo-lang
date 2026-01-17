package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class ClassTypeParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // This parselet is only called if the variable is a class type
        val className = token.value as String
        val classType = parser.context.getClassType(className)
            ?: throw IllegalArgumentException("Unknown class type: $className")
        return TypeParser.parseDefBody(parser, classType)
    }
}
