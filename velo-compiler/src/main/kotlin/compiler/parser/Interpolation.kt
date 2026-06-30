package compiler.parser

/**
 * A string literal that contains `$name` / `${expr}` interpolation, produced by
 * the lexer as a [Token]'s value (plain strings stay a `String`). The string
 * parselet expands the segments into a `+` chain, converting each expression to
 * a string via `.str`.
 */
data class Interpolation(val segments: List<StrSegment>)

sealed class StrSegment
data class StrLit(val text: String) : StrSegment()
data class StrExpr(val source: String) : StrSegment()
