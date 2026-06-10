package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.parser.*
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class DefParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val typeValue = token.value as? String
        val nextTokType = parser.peek()?.type
        return when {
            typeValue == CLASS && nextTokType == TokenType.VARIABLE -> {
                ClassParselet().parse(parser, token)
            }
            typeValue == FUNC && (nextTokType == TokenType.VARIABLE || parser.match(TokenType.PUNCTUATION, '(')) -> {
                FuncParselet().parse(parser, token)
            }
            else -> {
                val type = TypeParser.parseType(parser, token)
                TypeParser.parseDefBody(parser, type)
            }
        }
    }
}
