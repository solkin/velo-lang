package compiler.parser.parselets.literals

import compiler.nodes.BinaryNode
import compiler.nodes.Node
import compiler.nodes.PropNode
import compiler.nodes.StringNode
import compiler.parser.Interpolation
import compiler.parser.PrattParser
import compiler.parser.StrExpr
import compiler.parser.StrLit
import compiler.parser.StringInput
import compiler.parser.Token
import compiler.parser.TokenStream
import compiler.parser.VeloGrammar
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet

class StringParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val value = token.value
        if (value !is Interpolation) {
            return StringNode(value as String)
        }
        // Expand `"a${x}b"` into `"a" + (x).str + "b"`. Each hole is converted
        // with `.str`, so any type with a string conversion interpolates, and
        // the result is a left-leaning `+` chain (StrCon on strings).
        var node: Node? = null
        for (segment in value.segments) {
            val part: Node = when (segment) {
                is StrLit -> StringNode(segment.text)
                is StrExpr -> PropNode(name = "str", args = null, parent = subExpression(parser, segment.source))
            }
            node = if (node == null) part else BinaryNode(operator = "+", left = node, right = part)
        }
        return node ?: StringNode("")
    }

    /** Parse a `${...}` hole's source as an expression, reusing the enclosing
     * parser's type context (so class names etc. resolve the same way). */
    private fun subExpression(parser: ExpressionParser, source: String): Node {
        val sub = PrattParser(TokenStream(StringInput(source)), parser.context, parser.depLoader)
        VeloGrammar.configure(sub)
        return sub.parseExpression()
    }
}
