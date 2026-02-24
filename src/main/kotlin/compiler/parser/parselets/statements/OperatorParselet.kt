package compiler.parser.parselets.statements

import compiler.nodes.DefNode
import compiler.nodes.FuncNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.ParseException
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

class OperatorParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val opName = parseOperatorName(parser)

        val defsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val defs = defsNodes.map { it as DefNode }

        val name = if (opName == "op@-" && defs.isEmpty()) "op@neg" else opName

        val type = TypeParser.parseDefType(parser)
        val bodyList = parser.parseDelimited('{', '}', ';') {
            parser.parseExpression()
        }
        val body = when (bodyList.size) {
            0 -> VoidNode
            1 -> bodyList[0]
            else -> ProgramNode(prog = bodyList)
        }
        return FuncNode(
            name = name,
            native = false,
            defs = defs,
            type = type,
            body = body
        )
    }

    private val allowedOperators = setOf(
        "+", "-", "*", "/", "%",
        "==", "!=", "<", ">", "<=", ">=",
    )

    private fun parseOperatorName(parser: ExpressionParser): String {
        if (parser.match(TokenType.PUNCTUATION, '[')) {
            parser.consume(TokenType.PUNCTUATION, '[')
            parser.consume(TokenType.PUNCTUATION, ']')
            return if (parser.match(TokenType.OPERATOR, "=")) {
                parser.consume(TokenType.OPERATOR, "=")
                "op@[]="
            } else {
                "op@[]"
            }
        }
        if (parser.match(TokenType.OPERATOR)) {
            val op = parser.consume(TokenType.OPERATOR)
            if (op.value !in allowedOperators) {
                throw ParseException("Unsupported operator '${op.value}'. Allowed: ${allowedOperators.joinToString()}, [], []=")
            }
            return "op@${op.value}"
        }
        throw ParseException("Expected operator symbol after 'operator' keyword")
    }
}
