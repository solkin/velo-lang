package compiler.parser.parselets.statements

import compiler.nodes.FuncNode
import compiler.nodes.Node
import compiler.nodes.VoidNode
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class NativeParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // After "native", we expect either "class" or "func"
        val nextToken = parser.peek() ?: throw IllegalStateException("Expected 'class' or 'func' after 'native'")
        if (nextToken.type != TokenType.KEYWORD) {
            parser.peek()?.let { parser.consume(it.type) }
            throw IllegalStateException("Expected 'class' or 'func' after 'native'")
        }
        val keyword = nextToken.value as? String
        when (keyword) {
            "class" -> {
                parser.consume(TokenType.KEYWORD, "class")
                // Pass token with value "native" so ClassParselet knows it's native
                return ClassParselet().parse(parser, Token(TokenType.KEYWORD, "native"))
            }
            "func" -> {
                parser.consume(TokenType.KEYWORD, "func")
                val name = if (parser.match(TokenType.VARIABLE)) {
                    parser.consume(TokenType.VARIABLE).value as? String
                } else {
                    null
                }
                val defsNodes = parser.parseDelimited('(', ')', ',') {
                    TypeParser.parseDef(parser) as compiler.nodes.DefNode
                }
                val defs = defsNodes.map { it as compiler.nodes.DefNode }
                val type = TypeParser.parseDefType(parser)
                // Native functions don't have body
                return FuncNode(
                    name = name,
                    native = true,
                    defs = defs,
                    type = type,
                    body = VoidNode
                )
            }
            else -> {
                // Try to parse as definition (variable definition)
                return TypeParser.parseDef(parser)
            }
        }
    }
}
