package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.parser.ParseException
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses the `actor` prefix.
 *
 * Two surface forms share the keyword:
 *   - `actor class X(...) {...}` — declaration, delegated to [ClassParselet]
 *     with an `"actor"` marker token (mirroring how [NativeParselet] composes
 *     with [ClassParselet]). The body / generics / inheritance rules stay in
 *     a single place.
 *   - `actor[T] varname = ...` — variable declaration, handled by
 *     [DefParselet] which knows how to parse `actor[...]` via [TypeParser].
 *
 * Class-level invariants (no `actor native`, no generic actor, transferable
 * signatures) are enforced uniformly inside [compiler.nodes.ClassNode.compile].
 */
class ActorParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val next = parser.peek() ?: throw ParseException("Expected 'class' or '[' after 'actor'")
        if (next.type == TokenType.PUNCTUATION && next.value == '[') {
            return DefParselet().parse(parser, token)
        }
        if (next.type != TokenType.KEYWORD || next.value != "class") {
            throw ParseException("Expected 'class' or '[' after 'actor', got ${next.value}")
        }
        parser.consume(TokenType.KEYWORD, "class")
        return ClassParselet().parse(parser, Token(TokenType.KEYWORD, "actor"))
    }
}
