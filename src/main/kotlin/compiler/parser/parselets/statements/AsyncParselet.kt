package compiler.parser.parselets.statements

import compiler.nodes.AsyncNode
import compiler.nodes.Node
import compiler.nodes.PropNode
import compiler.parser.ParseException
import compiler.parser.Precedence
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

/**
 * Parses the `async` prefix expression: `async receiver.method(args)`.
 *
 * The grammar is intentionally restrictive — `<receiver>` must be a primary
 * (variable, parenthesised expression, etc.), followed by exactly one
 * `.method(args)`. Reasoning:
 *
 *   - Pratt precedence cannot express "consume one `.foo()` call but not the
 *     subsequent chain" because `.` always has the same precedence. Letting
 *     `async` greedily consume the whole expression would leave no room for
 *     post-processing (`(async x.foo()).somethingElse` is meaningless;
 *     post-processing belongs after `await`, not on the future itself).
 *   - Forcing the actor call to be the outermost form keeps the rule simple:
 *     "every `async` produces exactly one cross-thread call". For chained
 *     receivers users explicitly parenthesise: `async (foo()).method()`.
 */
class AsyncParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        // Tight precedence here ensures we stop before consuming the trailing
        // `.method(args)` — that part is parsed by hand below.
        val receiver = parser.parseExpression(Precedence.PROPERTY)
        if (!parser.match(TokenType.PUNCTUATION, '.')) {
            throw ParseException("'async' must be followed by a method call: async receiver.method(args)")
        }
        parser.consume(TokenType.PUNCTUATION, '.')
        val methodName = parser.consume(TokenType.VARIABLE).value as String
        if (!parser.match(TokenType.PUNCTUATION, '(')) {
            throw ParseException("'async' requires a method call (with parentheses): async receiver.$methodName(...)")
        }
        val args = parser.parseDelimited('(', ')', ',') { parser.parseExpression() }
        return AsyncNode(PropNode(name = methodName, args = args, parent = receiver))
    }
}
