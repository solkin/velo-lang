package compiler.parser

data class Token(
    val type: TokenType,
    val value: Any,
)

enum class TokenType(val type: String) {
    NUMBER("num"),
    KEYWORD("kw"),
    VARIABLE("var"),
    STRING("str"),
    PUNCTUATION("punc"),
    OPERATOR("op"),
}