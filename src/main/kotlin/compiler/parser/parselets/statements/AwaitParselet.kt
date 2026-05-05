package compiler.parser.parselets.statements

import compiler.nodes.AwaitNode
import compiler.nodes.Node
import compiler.parser.Precedence
import compiler.parser.Token
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses the `await` prefix expression.
 *
 * Surface grammar: `await <expr>`. The semantic restriction "<expr> must be
 * a future" is enforced in [AwaitNode.compile] from the type of the
 * compiled inner expression — not by the grammar — because future-typed
 * values can come from many places (`async ...`, a variable, a function
 * call, an indexed array).
 *
 * Precedence: parses with [Precedence.PROPERTY] so that a trailing
 * `.something` after the awaited value belongs to the *result*, not to the
 * future itself. That makes `(await async x.foo()).str` parse as
 * "await the future, then access `.str` on the resulting string", which is
 * what users intuitively want.
 */
class AwaitParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val expr = parser.parseExpression(Precedence.PROPERTY)
        return AwaitNode(expr)
    }
}
