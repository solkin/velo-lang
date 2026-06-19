package compiler.parser.parselets.statements

import compiler.nodes.AsyncNode
import compiler.nodes.AwaitNode
import compiler.nodes.Node
import compiler.nodes.PropNode
import compiler.parser.Precedence
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses the `await` prefix expression.
 *
 * Two surface forms:
 *   - `await <future>` — drain a `future[T]` (variable, `async ...`, etc.).
 *   - `await receiver.method(args)` — synchronous actor call: `async` is
 *     implied, so this is sugar for `await async receiver.method(args)`. One
 *     visible boundary marker instead of two keywords; `async` is still used
 *     on its own when you want the `future[T]` for parallelism.
 *
 * Mechanics: the receiver is parsed at [Precedence.PROPERTY], which stops
 * before a trailing `.` — the same reason `(await async x.foo()).str` binds
 * `.str` to the *result*. A following `.name(` is therefore the implied actor
 * call (wrapped in [AsyncNode] here); a following `.name` without `(` is an
 * ordinary property access on the awaited result — `(await receiver).name`.
 * The sugar is skipped when the receiver is already an explicit `async ...`,
 * so `await async x.foo().bar()` keeps meaning `(await async x.foo()).bar()`.
 *
 * The "<future> only" restriction for the non-sugar form is still enforced in
 * [AwaitNode.compile] from the inner expression's type.
 */
class AwaitParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val receiver = parser.parseExpression(Precedence.PROPERTY)
        if (receiver !is AsyncNode && parser.match(TokenType.PUNCTUATION, '.')) {
            parser.consume(TokenType.PUNCTUATION, '.')
            val name = parser.consume(TokenType.VARIABLE).value as String
            if (parser.match(TokenType.PUNCTUATION, '(')) {
                val args = parser.parseDelimited('(', ')', ',') { parser.parseExpression() }
                return AwaitNode(AsyncNode(PropNode(name = name, args = args, parent = receiver)))
            }
            return PropNode(name = name, args = null, parent = AwaitNode(receiver))
        }
        return AwaitNode(receiver)
    }
}
