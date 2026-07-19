package compiler.parser.parselets.statements

import compiler.nodes.LiteralPattern
import compiler.nodes.Node
import compiler.nodes.Pattern
import compiler.nodes.VariantPattern
import compiler.nodes.WhenArm
import compiler.nodes.WhenNode
import compiler.parser.ParseException
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

/**
 * Parses `when subject { pattern -> body ... else -> body }`.
 *
 * The subject is parsed with `allowApply = false` so the following `{` opens the
 * arm block rather than an apply-block. Arms are separated by a newline (the
 * automatic `;`) or an explicit comma; each is `pattern -> expression`, and the
 * catch-all is `else -> expression`. A pattern is either a value expression
 * (a literal switch) or a variant name with optional field bindings
 * (`Add(l, r)`, or bare `None`).
 */
class WhenParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val subject = parser.parseExpression(allowApply = false)
        parser.consume(TokenType.PUNCTUATION, '{')

        val arms = mutableListOf<WhenArm>()
        var elseBody: Node? = null
        while (!parser.match(TokenType.PUNCTUATION, '}')) {
            if (parser.match(TokenType.PUNCTUATION, ';')) { parser.consume(TokenType.PUNCTUATION, ';'); continue }
            if (parser.match(TokenType.PUNCTUATION, ',')) { parser.consume(TokenType.PUNCTUATION, ','); continue }

            if (parser.match(TokenType.KEYWORD, "else")) {
                parser.consume(TokenType.KEYWORD, "else")
                parser.consume(TokenType.OPERATOR, "->")
                if (elseBody != null) throw ParseException("`when` has more than one `else` arm")
                elseBody = parser.parseExpression()
                continue
            }

            val pattern = parsePattern(parser)
            parser.consume(TokenType.OPERATOR, "->")
            val body = parser.parseExpression()
            arms.add(WhenArm(pattern, body))
        }
        parser.consume(TokenType.PUNCTUATION, '}')

        if (arms.isEmpty() && elseBody == null) throw ParseException("`when` has no arms")
        return WhenNode(subject, arms, elseBody)
    }

    private fun parsePattern(parser: ExpressionParser): Pattern {
        // A variant pattern begins with an identifier (the variant name); anything
        // else (a number, string, `true`/`false`, `-1`) is a value pattern.
        if (parser.peek()?.type == TokenType.VARIABLE) {
            val name = TypeParser.parseVarname(parser)
            val bindings = if (parser.match(TokenType.PUNCTUATION, '(')) {
                parser.parseDelimited('(', ')', ',') { NameNode(TypeParser.parseVarname(parser)) }
                    .map { (it as NameNode).name }
            } else {
                emptyList()
            }
            return VariantPattern(name, bindings)
        }
        return LiteralPattern(parser.parseExpression())
    }
}

/** Throwaway holder so a binding name can flow through [ExpressionParser.parseDelimited]. */
private data class NameNode(val name: String) : Node()
