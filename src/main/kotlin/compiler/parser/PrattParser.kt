package compiler.parser

import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.parselets.PrefixParselet

class PrattParser(
    private val stream: TokenStream,
    override val context: ParserContext,
    override val depLoader: DependencyLoader
) : ExpressionParser {

    private val prefixParselets = mutableMapOf<TokenType, PrefixParselet>()
    private val keywordParselets = mutableMapOf<String, PrefixParselet>()
    private val operatorParselets = mutableMapOf<String, PrefixParselet>()
    private val infixParselets = mutableMapOf<String, InfixParselet>()
    private var allowApply = true

    fun register(type: TokenType, parselet: PrefixParselet) {
        prefixParselets[type] = parselet
    }

    fun registerKeyword(keyword: String, parselet: PrefixParselet) {
        keywordParselets[keyword] = parselet
    }

    fun registerOperator(operator: String, parselet: PrefixParselet) {
        operatorParselets[operator] = parselet
    }

    fun registerInfix(operator: String, precedence: Int, parselet: InfixParselet) {
        infixParselets[operator] = parselet
    }

    override fun parseExpression(precedence: Int): Node {
        var token = stream.next() ?: throw ParseException("Unexpected end of input")
        
        // Check for keyword first
        val prefix = when (token.type) {
            TokenType.KEYWORD -> {
                keywordParselets[token.value as? String]
            }
            TokenType.OPERATOR -> {
                operatorParselets[token.value as? String]
            }
            else -> if (token.type == TokenType.VARIABLE) {
                // Check if variable is a class type and next token is also VARIABLE (variable name)
                val varName = token.value as? String
                val nextToken = stream.peek()
                if (varName != null && context.isClassType(varName) &&
                    nextToken?.type == TokenType.VARIABLE
                ) {
                    keywordParselets["__class_type__"]
                } else {
                    null
                }
            } else {
                null
            } ?: prefixParselets[token.type] ?: throw ParseException("Unexpected token: $token")
        }

        val prefixParselet = prefix ?: throw ParseException("Unexpected token: $token")
        var left = prefixParselet.parse(this, token)

        while (precedence < getNextPrecedence()) {
            token = stream.next() ?: break
            val infix = getInfixParselet(token) ?: break
            left = infix.parse(this, left, token)
        }

        return left
    }

    override fun parseExpression(allowApply: Boolean): Node {
        val oldAllowApply = this.allowApply
        this.allowApply = allowApply
        try {
            return parseExpression(0)
        } finally {
            this.allowApply = oldAllowApply
        }
    }

    private fun getNextPrecedence(): Int {
        val token = stream.peek() ?: return 0
        val infix = getInfixParselet(token) ?: return 0
        return infix.precedence
    }

    private fun getInfixParselet(token: Token): InfixParselet? {
        return when (token.type) {
            TokenType.OPERATOR -> infixParselets[token.value as? String]
            TokenType.PUNCTUATION -> {
                val punct = token.value as? Char
                when (punct) {
                    '(' -> infixParselets["("]
                    '[' -> infixParselets["["]
                    '.' -> infixParselets["."]
                    '{' -> if (allowApply) infixParselets["{"] else null
                    else -> null
                }
            }
            else -> null
        }
    }

    override fun parseDelimited(start: Char, stop: Char, separator: Char, parser: () -> Node): List<Node> {
        val result = mutableListOf<Node>()
        var first = true
        consume(TokenType.PUNCTUATION, start)
        while (!eof()) {
            if (match(TokenType.PUNCTUATION, stop)) {
                break
            }
            if (first) {
                first = false
            } else {
                consume(TokenType.PUNCTUATION, separator)
            }
            if (match(TokenType.PUNCTUATION, stop)) {
                break
            }
            result.add(parser())
        }
        consume(TokenType.PUNCTUATION, stop)
        return result
    }

    override fun parseDelimited(separator: Char, parser: () -> Node): List<Node> {
        val result = mutableListOf<Node>()
        var first = true
        while (!eof()) {
            if (first) {
                first = false
            } else {
                if (!match(TokenType.PUNCTUATION, separator)) {
                    break
                }
                consume(TokenType.PUNCTUATION, separator)
            }
            result.add(parser())
        }
        return result
    }

    override fun consume(type: TokenType, value: Any?): Token {
        val token = stream.next()
        if (token == null) {
            stream.croak("Unexpected end of input, expected $type${if (value != null) " with value $value" else ""}")
            throw ParseException("Unexpected end of input")
        }
        if (token.type != type) {
            stream.croak("Expected $type but got ${token.type}")
            throw ParseException("Expected $type but got ${token.type}")
        }
        if (value != null && token.value != value) {
            stream.croak("Expected $type with value $value but got ${token.value}")
            throw ParseException("Expected $type with value $value but got ${token.value}")
        }
        return token
    }

    override fun match(type: TokenType, value: Any?): Boolean {
        val token = stream.peek() ?: return false
        if (token.type != type) return false
        if (value != null && token.value != value) return false
        return true
    }

    override fun peek(): Token? {
        return stream.peek()
    }

    override fun eof(): Boolean {
        return stream.eof()
    }
}

class ParseException(message: String) : Exception(message)
