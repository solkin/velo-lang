package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.nodes.ThrowNode
import compiler.parser.SourceLoader
import compiler.parser.Token
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses `throw expr`. The value is an `Error` (or a bare string literal, which
 * [ThrowNode] lowers to `new Error(ERR_GENERIC, "text")`). Requesting
 * `std/error` makes `Error`/`ERR_*` available without an explicit import.
 */
class ThrowParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val value = parser.parseExpression()
        parser.context.requireModule(SourceLoader.STDLIB_ERROR)
        return ThrowNode(value = value)
    }
}
