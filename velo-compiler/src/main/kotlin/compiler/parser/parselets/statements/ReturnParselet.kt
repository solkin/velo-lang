package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.nodes.ReturnNode
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class ReturnParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // `return` with no value when a statement terminator immediately follows.
        if (parser.eof() ||
            parser.match(TokenType.PUNCTUATION, ';') ||
            parser.match(TokenType.PUNCTUATION, '}')
        ) {
            return ReturnNode(value = null)
        }
        return ReturnNode(value = parser.parseExpression())
    }
}
