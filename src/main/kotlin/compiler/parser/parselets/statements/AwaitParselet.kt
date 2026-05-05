package compiler.parser.parselets.statements

import compiler.nodes.AwaitNode
import compiler.nodes.Node
import compiler.nodes.PropNode
import compiler.parser.ParseException
import compiler.parser.Precedence
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses the `await` prefix expression: `await receiver.method(args)`.
 *
 * The grammar is intentionally restrictive — `<receiver>` must be a primary
 * (variable, parenthesised expression, etc.), followed by exactly one
 * `.method(args)`. Reasoning:
 *
 *   - Pratt precedence cannot express "consume one `.foo()` call but not the
 *     subsequent chain" because `.` always has the same precedence. Letting
 *     `await` greedily consume the whole expression would make trailing
 *     post-processing (`(await x).str` style) syntactically ambiguous.
 *   - Forcing the actor call to be the outermost form keeps the rule simple:
 *     "every `await` produces exactly one cross-thread call". For chained
 *     receivers users explicitly parenthesise: `await (foo()).method()`. For
 *     post-processing they parenthesise the await: `(await x.foo()).str`.
 */
class AwaitParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Tight precedence here ensures we stop before consuming the trailing
        // `.method(args)` — that part is parsed by hand below.
        val receiver = parser.parseExpression(Precedence.PROPERTY)
        if (!parser.match(TokenType.PUNCTUATION, '.')) {
            throw ParseException("'await' must be followed by a method call: await receiver.method(args)")
        }
        parser.consume(TokenType.PUNCTUATION, '.')
        val methodName = parser.consume(TokenType.VARIABLE).value as String
        if (!parser.match(TokenType.PUNCTUATION, '(')) {
            throw ParseException("'await' requires a method call (with parentheses): await receiver.$methodName(...)")
        }
        val args = parser.parseDelimited('(', ')', ',') { parser.parseExpression() }
        return AwaitNode(PropNode(name = methodName, args = args, parent = receiver))
    }
}
