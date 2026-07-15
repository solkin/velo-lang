package compiler.parser.parselets.statements

import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.TryNode
import compiler.nodes.VoidNode
import compiler.parser.ParseException
import compiler.parser.SourceLoader
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses `try { ... } catch (Error e) { ... }`.
 *
 * Both bodies parse like a `while` body (a brace-delimited statement list); the
 * `catch` keyword is consumed inline, the way [IfParselet] consumes `else`. The
 * caught type is fixed to `Error` — the single error value type (VEL-9) — so we
 * accept exactly that name rather than resolving an arbitrary type (which the
 * auto-imported `std/error` has not registered yet at parse time). Requesting
 * `std/error` here is what makes `Error`/`ERR_*` available without an explicit
 * import.
 */
class TryParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val tryBody = parser.parseDelimited('{', '}', ';') { parser.parseExpression() }

        parser.consume(TokenType.KEYWORD, "catch")
        parser.consume(TokenType.PUNCTUATION, '(')
        val typeName = parser.consume(TokenType.VARIABLE).value as String
        if (typeName != "Error") {
            throw ParseException("catch requires the Error type, got '$typeName'")
        }
        val errName = parser.consume(TokenType.VARIABLE).value as String
        parser.consume(TokenType.PUNCTUATION, ')')

        val catchBody = parser.parseDelimited('{', '}', ';') { parser.parseExpression() }

        parser.context.requireModule(SourceLoader.STDLIB_ERROR)
        return TryNode(tryBody = fold(tryBody), errName = errName, catchBody = fold(catchBody))
    }

    private fun fold(nodes: List<Node>): Node = when (nodes.size) {
        0 -> VoidNode
        1 -> nodes[0]
        else -> ProgramNode(prog = nodes)
    }
}
