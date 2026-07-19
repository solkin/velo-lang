package compiler.parser

import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.InfixParselet
import compiler.parser.parselets.PrefixParselet

class PrattParser(
    private val stream: TokenStream,
    override val context: ParserContext,
    override val depLoader: DependencyLoader,
    override var currentDir: java.io.File? = null,
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
        token = rewriteNamespace(token)

        // Check for keyword first
        val prefix = when (token.type) {
            TokenType.KEYWORD -> {
                keywordParselets[token.value as? String]
            }
            TokenType.OPERATOR -> {
                operatorParselets[token.value as? String]
            }
            else -> if (token.type == TokenType.VARIABLE) {
                val varName = token.value as? String
                val nextToken = stream.peek()
                if (varName != null && context.isClassType(varName)) {
                    val classType = context.getClassType(varName)
                    val isGenericTypeUsage = classType?.typeParams?.isNotEmpty() == true
                            && nextToken?.type == TokenType.PUNCTUATION && nextToken.value == '['
                    if (nextToken?.type == TokenType.VARIABLE || isGenericTypeUsage) {
                        keywordParselets["__class_type__"]
                    } else {
                        null
                    }
                } else if (varName != null && context.isInterfaceType(varName) &&
                    nextToken?.type == TokenType.VARIABLE
                ) {
                    // `InterfaceName varname ...` — a definition typed by a
                    // structural interface.
                    keywordParselets["__class_type__"]
                } else if (varName != null && context.isEnumType(varName) &&
                    nextToken?.type == TokenType.VARIABLE
                ) {
                    // `EnumName varname ...` — a definition typed by an enum.
                    keywordParselets["__class_type__"]
                } else if (varName != null && context.isNativeType(varName) &&
                    nextToken?.type == TokenType.VARIABLE
                ) {
                    // `RegisteredHostClass varname ...` — a definition typed by
                    // a native class synthesized from the registry.
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

    /**
     * Namespaced-import resolution at the point an identifier is read:
     *  - `ns.member` (outside a namespaced module) → the mangled name `ns$member`;
     *  - a reference to the current namespaced module's own top-level name → its
     *    mangled form. Everything else passes through unchanged.
     */
    private fun rewriteNamespace(token: Token): Token {
        if (token.type != TokenType.VARIABLE) return token
        val name = token.value as? String ?: return token
        if (context.isNamespace(name)) {
            val dot = stream.peek()
            if (dot?.type == TokenType.PUNCTUATION && dot.value == '.') {
                stream.next()
                val member = stream.next()?.value as? String
                    ?: throw ParseException("Expected a name after '$name.'")
                return Token(TokenType.VARIABLE, "$name\$$member")
            }
        }
        val mangled = context.localRef(name)
        return if (mangled == name) token else Token(TokenType.VARIABLE, mangled)
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
