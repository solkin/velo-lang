package compiler.parser.parselets

import compiler.nodes.Node
import compiler.parser.DependencyLoader
import compiler.parser.ParserContext
import compiler.parser.Token
import compiler.parser.TokenType

interface PrefixParselet {
    fun parse(parser: ExpressionParser, token: Token): Node
}

interface InfixParselet {
    val precedence: Int
    val isRightAssociative: Boolean get() = false
    fun parse(parser: ExpressionParser, left: Node, token: Token): Node
}

interface ExpressionParser {
    fun parseExpression(precedence: Int = 0): Node
    fun parseExpression(allowApply: Boolean): Node
    fun parseDelimited(start: Char, stop: Char, separator: Char, parser: () -> Node): List<Node>
    fun parseDelimited(separator: Char, parser: () -> Node): List<Node>
    fun consume(type: TokenType, value: Any? = null): Token
    fun match(type: TokenType, value: Any? = null): Boolean
    fun peek(): Token?
    fun eof(): Boolean
    val context: ParserContext
    val depLoader: DependencyLoader
}
