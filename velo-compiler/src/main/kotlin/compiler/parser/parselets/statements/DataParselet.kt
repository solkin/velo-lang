package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.parser.ParseException
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses the `data` prefix: `data class X(...) {...}`.
 *
 * A data class is an immutable value type — its state is exactly its
 * constructor parameters and it is copied (not aliased) across actor and
 * native boundaries. Parsing is delegated to [ClassParselet] with a `"data"`
 * marker token, mirroring how [ActorParselet] composes with it; the
 * value-type invariants (immutable fields, transferable fields, methods-only
 * body) are enforced in [compiler.nodes.ClassNode.compile].
 */
class DataParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val next = parser.peek() ?: throw ParseException("Expected 'class' after 'data'")
        if (next.type != TokenType.KEYWORD || next.value != "class") {
            throw ParseException("Expected 'class' after 'data', got ${next.value}")
        }
        parser.consume(TokenType.KEYWORD, "class")
        return ClassParselet().parse(parser, Token(TokenType.KEYWORD, "data"))
    }
}
