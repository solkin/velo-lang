package compiler.parser.parselets.statements

import compiler.nodes.IfNode
import compiler.nodes.Node
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class IfParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val condNode = parser.parseExpression(allowApply = false)
        // Check if next token is 'then' keyword or '{' punctuation
        val hasThen = parser.match(TokenType.KEYWORD, "then")
        val hasBlock = parser.match(TokenType.PUNCTUATION, '{')
        if (hasThen) {
            parser.consume(TokenType.KEYWORD, "then")
        } else if (!hasBlock) {
            // If neither 'then' nor '{', it's an error in original parser
            // But we'll try to parse anyway
        }
        val thenNode = parser.parseExpression()
        val elseNode = if (parser.match(TokenType.KEYWORD, "else")) {
            parser.consume(TokenType.KEYWORD, "else")
            parser.parseExpression()
        } else {
            null
        }
        return IfNode(condNode, thenNode, elseNode)
    }
}
