package compiler.parser

class TokenStream(private val input: Input) {

    private var current: Token? = null
    private val keywords = setOf(
        "if",
        "then",
        "else",
        "while",
        "arrayOf",
        "dictOf",
        "tupleOf",
        "class",
        "func",
        "native",
        "let",
        "true",
        "false"
    ).plus(stdTypesSet)

    private fun isKeyword(str: String): Boolean {
        return keywords.contains(str)
    }

    private fun isDigit(ch: Char): Boolean {
        return "0123456789".indexOf(ch) >= 0
    }

    private fun isIdStart(ch: Char): Boolean {
        return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_".indexOf(ch) >= 0
    }

    private fun isId(ch: Char): Boolean {
        return isIdStart(ch) || "0123456789".indexOf(ch) >= 0
    }

    private fun isOpChar(ch: Char): Boolean {
        return "+-*/%=&|<>!".indexOf(ch) >= 0
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

    private fun readNumber(): Token {
        var hasDot = false
        val number = readWhile(predicate = fun(ch: Char): Boolean {
            if (ch == '.') {
                if (hasDot) {
                    // Dot was already been set - stop accumulating number
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
                hasDot = true
                return true
            }
            return isDigit(ch)
        })
        val value = if (hasDot) number.toDouble() else number.toInt()
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

    private fun readEscaped(): String {
        var escaped = false
        var str = ""
        input.next()
        while (!input.eof()) {
            val ch = input.next()
            if (escaped) {
                str += when(ch) {
                    'n' -> '\n'
                    't' -> '\r'
                    else -> ch
                }
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                break
            } else {
                str += ch
            }
        }
        return str
    }

    private fun readString(): Token {
        return Token(
            type = TokenType.STRING,
            value = readEscaped()
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

    private fun skipComment() {
        readWhile(predicate = fun(ch: Char): Boolean { return ch != '\n' })
        input.next()
    }

    private fun readNext(): Token? {
        readWhile(predicate = ::isWhitespace)
        if (input.eof()) return null
        val ch = input.peek()
        when {
            ch == '#' -> return skipComment().run { readNext() }
            ch == '"' -> return readString()
            isDigit(ch) -> return readNumber()
            isIdStart(ch) -> return readIdent()
            isPunctuation(ch) -> return readPunctuation()
            isOpChar(ch) -> return readOperator()
        }
        input.croak("Can't handle character: $ch")
        return null
    }

    fun peek(): Token? {
        return current ?: next()?.apply { current = this }
    }

    fun next(): Token? {
        val token = current
        current = null
        return token ?: readNext()
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
)
