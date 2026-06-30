package compiler.parser

class TokenStream(private val input: Input) {

    private var current: Token? = null
    private val keywords = setOf(
        "if",
        "then",
        "else",
        "while",
        "new",
        "actor",
        "data",
        "async",
        "await",
        "ext",
        "let",
        "true",
        "false",
        "null",
        "include",
        "operator",
        "interface",
        "return",
        "break",
        "continue",
        "for",
        "in",
    ).plus(stdTypesSet)

    private fun isKeyword(str: String): Boolean {
        return keywords.contains(str)
    }

    private fun isDigit(ch: Char): Boolean {
        return "0123456789".indexOf(ch) >= 0
    }

    private fun isHexDigit(ch: Char): Boolean {
        return "0123456789ABCDEFabcdef".indexOf(ch) >= 0
    }

    private fun isBinDigit(ch: Char): Boolean {
        return "01".indexOf(ch) >= 0
    }

    private fun isIdStart(ch: Char): Boolean {
        return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_".indexOf(ch) >= 0
    }

    private fun isId(ch: Char): Boolean {
        return isIdStart(ch) || "0123456789".indexOf(ch) >= 0
    }

    private fun isOpChar(ch: Char): Boolean {
        return "+-*/%=&|^<>!".indexOf(ch) >= 0
    }

    private fun isPunctuation(ch: Char): Boolean {
        return ".,:;(){}[]".indexOf(ch) >= 0
    }

    private fun isWhitespace(ch: Char): Boolean {
        return " \t\n\r".indexOf(ch) >= 0
    }

    private fun readWhile(predicate: (Char) -> Boolean): String {
        var str = ""
        while (!input.eof() && predicate(input.peek())) {
            str += input.next()
        }
        return str
    }

    private enum class NumberFormat{
        STD, FLOAT, BYTE, HEX, BIN
    }

    private fun readNumber(): Token {
        var format = NumberFormat.STD
        var rawNumber = ""
        val number = readWhile(predicate = fun(ch: Char): Boolean {
            rawNumber += ch
            when (ch) {
                '_' -> return true // Skip delimiter

                '.' -> {
                    if (format != NumberFormat.STD) {
                        // Number format is already non-standard - stop accumulating number
                        return false
                    }
                    // Check for next after '.' symbol is digit, or it is property start
                    try {
                        input.mark()
                        input.next()
                        if (!isDigit(input.peek())) {
                            // Return to the previous char so that '.' will be available to read
                            return false
                        }
                    } finally {
                        input.reset()
                    }
                    format = NumberFormat.FLOAT
                    return true
                }

                'x' -> {
                    val prefix = rawNumber.take(rawNumber.length - 1)
                    if (format != NumberFormat.STD) {
                        // Number format is already non-standard - stop accumulating number
                        return false
                    }
                    if (Integer.parseInt(prefix) == 0) {
                        format = NumberFormat.HEX
                        return true
                    } else {
                        // Invalid prefix - stop accumulating number
                        return false
                    }
                }

                'b' -> {
                    val prefix = rawNumber.take(rawNumber.length - 1)
                    if (format != NumberFormat.STD) {
                        // Number format is already non-standard - stop accumulating number
                        return false
                    }
                    if (Integer.parseInt(prefix) == 0) {
                        format = NumberFormat.BIN
                        return true
                    } else {
                        // Invalid prefix - stop accumulating number
                        return false
                    }
                }

                'y' -> {
                    format = NumberFormat.BYTE
                    input.next()
                    return false
                }

                'f' -> {
                    if (format == NumberFormat.STD || format == NumberFormat.FLOAT) {
                        format = NumberFormat.FLOAT
                        input.next()
                        return false
                    }
                }
            }
            return when (format) {
                NumberFormat.STD -> isDigit(ch)
                NumberFormat.FLOAT -> isDigit(ch)
                NumberFormat.BYTE -> isDigit(ch)
                NumberFormat.HEX -> isHexDigit(ch)
                NumberFormat.BIN -> isBinDigit(ch)
            }
        }).replace("_", "")
        val value =  when (format) {
            NumberFormat.STD -> number.toInt()
            NumberFormat.FLOAT -> number.toFloat()
            NumberFormat.BYTE -> number.toByte()
            NumberFormat.HEX -> number.substringAfter('x')
                .takeIf { !it.isEmpty() }
                ?.toInt(radix = 16) ?: 0
            NumberFormat.BIN -> number.substringAfter('b')
                .takeIf { !it.isEmpty() }
                ?.toInt(radix = 2) ?: 0
        }
        return Token(
            type = TokenType.NUMBER,
            value = value
        )
    }

    private fun readIdent(): Token {
        val id = readWhile(::isId)
        return Token(
            type = if (isKeyword(id)) TokenType.KEYWORD else TokenType.VARIABLE,
            value = id
        )
    }

    private fun readEscaped(delimiter: Char): String {
        var escaped = false
        var str = ""
        input.next()
        while (!input.eof()) {
            val ch = input.next()
            if (escaped) {
                str += when (ch) {
                    'n' -> '\n'
                    't' -> '\r'
                    else -> ch
                }
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                break
            } else {
                str += ch
            }
        }
        return str
    }

    private fun readString(): Token {
        // Recognises `$name` and `${expr}` interpolation. A string with no
        // interpolation keeps a plain `String` value; otherwise the value is an
        // [Interpolation] the string parselet expands into a concatenation.
        input.next() // opening quote
        val segments = mutableListOf<StrSegment>()
        val literal = StringBuilder()
        fun flush() {
            if (literal.isNotEmpty()) {
                segments.add(StrLit(literal.toString()))
                literal.clear()
            }
        }
        var escaped = false
        while (!input.eof()) {
            val ch = input.next()
            if (escaped) {
                literal.append(
                    when (ch) {
                        'n' -> '\n'
                        't' -> '\r'
                        else -> ch
                    }
                )
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                break
            } else if (ch == '$' && !input.eof() && (input.peek() == '{' || isIdStart(input.peek()))) {
                flush()
                if (input.peek() == '{') {
                    input.next() // consume '{'
                    segments.add(StrExpr(readInterpolationExpr()))
                } else {
                    segments.add(StrExpr(readWhile(::isId)))
                }
            } else {
                literal.append(ch)
            }
        }
        flush()
        return when {
            segments.isEmpty() -> Token(type = TokenType.STRING, value = "")
            segments.size == 1 && segments[0] is StrLit ->
                Token(type = TokenType.STRING, value = (segments[0] as StrLit).text)
            else -> Token(type = TokenType.STRING, value = Interpolation(segments))
        }
    }

    /** Read the source of a `${...}` hole up to the matching `}` (the `{` is
     * already consumed), tolerating nested braces and quoted strings. */
    private fun readInterpolationExpr(): String {
        val sb = StringBuilder()
        var depth = 1
        while (!input.eof()) {
            val ch = input.next()
            when (ch) {
                '{' -> { depth++; sb.append(ch) }
                '}' -> { depth--; if (depth == 0) return sb.toString() else sb.append(ch) }
                '"' -> {
                    sb.append(ch)
                    var esc = false
                    while (!input.eof()) {
                        val c = input.next()
                        sb.append(c)
                        if (esc) {
                            esc = false
                        } else if (c == '\\') {
                            esc = true
                        } else if (c == '"') {
                            break
                        }
                    }
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun readChar(): Token {
        val escStr = readEscaped(delimiter = '\'')
        val charArr = escStr.toCharArray()
        if (charArr.size != 1) {
            croak("Invalid char syntax")
        }
        return Token(
            type = TokenType.NUMBER,
            value = charArr.single().code
        )
    }

    private fun readOperator(): Token {
        return Token(
            type = TokenType.OPERATOR,
            value = readWhile(::isOpChar)
        )
    }

    private fun readPunctuation(): Token {
        return Token(
            type = TokenType.PUNCTUATION,
            value = input.next()
        )
    }

    /**
     * Skip whitespace and `#` line comments, recording in [sawNewline] whether
     * a line break (or comment, which ends the line) was crossed — the signal
     * the automatic-semicolon-insertion pass keys off.
     */
    private fun skipWhitespaceAndComments() {
        while (!input.eof()) {
            val ch = input.peek()
            when {
                ch == '\n' -> { sawNewline = true; input.next() }
                isWhitespace(ch) -> input.next()
                ch == '#' -> {
                    readWhile(predicate = fun(c: Char): Boolean { return c != '\n' })
                    sawNewline = true
                    if (!input.eof()) input.next()
                }
                else -> return
            }
        }
    }

    private fun readRawToken(): Token? {
        skipWhitespaceAndComments()
        if (input.eof()) return null
        val ch = input.peek()
        when {
            ch == '"' -> return readString()
            ch == '\'' -> return readChar()
            isDigit(ch) -> return readNumber()
            isIdStart(ch) -> return readIdent()
            ch == '.' -> return readDot()
            isPunctuation(ch) -> return readPunctuation()
            isOpChar(ch) -> return readOperator()
        }
        input.croak("Can't handle character: $ch")
        return null
    }

    /** `..` (the range operator) vs a single `.` (property access). */
    private fun readDot(): Token {
        input.next()
        if (!input.eof() && input.peek() == '.') {
            input.next()
            return Token(type = TokenType.OPERATOR, value = "..")
        }
        return Token(type = TokenType.PUNCTUATION, value = '.')
    }

    // ---- Automatic semicolon insertion ----
    //
    // Newlines terminate statements (Go-style): a synthetic `;` is produced
    // when a line break separates a token that can *end* a statement from one
    // that cannot *continue* the previous expression. Explicit `;` keeps
    // working, and a statement may still span multiple lines as long as it
    // breaks after an operator/`.`/`,`/open bracket (a continuer) — so no `;`
    // is ever needed after `}`.

    private var prev: Token? = null
    private var pending: Token? = null
    private var sawNewline = false

    private val enderKeywords = setOf("true", "false", "null", "return", "break", "continue")
    private val continuerKeywords = setOf("else", "then")

    private fun isEnder(t: Token): Boolean = when (t.type) {
        TokenType.NUMBER, TokenType.STRING, TokenType.VARIABLE -> true
        TokenType.KEYWORD -> (t.value as? String) in enderKeywords
        TokenType.PUNCTUATION -> t.value == ')' || t.value == ']' || t.value == '}'
        else -> false
    }

    private fun isContinuer(t: Token): Boolean = when (t.type) {
        TokenType.OPERATOR -> true
        TokenType.KEYWORD -> (t.value as? String) in continuerKeywords
        TokenType.PUNCTUATION ->
            t.value == '.' || t.value == ',' || t.value == ';' ||
                t.value == ')' || t.value == ']' || t.value == '}'
        else -> false
    }

    private fun produce(): Token? {
        pending?.let { pending = null; prev = it; return it }
        sawNewline = false
        val token = readRawToken() ?: run { prev = null; return null }
        if (sawNewline && prev?.let { isEnder(it) } == true && !isContinuer(token)) {
            pending = token
            val semicolon = Token(type = TokenType.PUNCTUATION, value = ';')
            prev = semicolon
            return semicolon
        }
        prev = token
        return token
    }

    fun peek(): Token? {
        return current ?: produce()?.also { current = it }
    }

    fun next(): Token? {
        val token = current
        current = null
        return token ?: produce()
    }

    fun eof(): Boolean {
        return peek() == null
    }

    fun croak(msg: String) {
        input.croak(msg)
    }

}

const val BYTE = "byte"
const val INT = "int"
const val FLOAT = "float"
const val STR = "str"
const val BOOL = "bool"
const val TUPLE = "tuple"
const val ARRAY = "array"
const val DICT = "dict"
const val CLASS = "class"
const val FUNC = "func"
const val VOID = "void"
const val ANY = "any"
const val PTR = "ptr"
const val ACTOR = "actor"
const val DATA = "data"
const val FUTURE = "future"
const val SELF = "Self"

val stdTypesSet = setOf(
    BYTE,
    INT,
    FLOAT,
    STR,
    BOOL,
    TUPLE,
    ARRAY,
    DICT,
    CLASS,
    FUNC,
    VOID,
    ANY,
    PTR,
    ACTOR,
    FUTURE,
    SELF,
)
