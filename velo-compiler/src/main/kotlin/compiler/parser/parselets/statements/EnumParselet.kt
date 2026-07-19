package compiler.parser.parselets.statements

import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DefNode
import compiler.nodes.EnumType
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

/**
 * Parses an `enum Name { Variant(fields) ... }` declaration — a closed sum type.
 *
 * Each variant lowers to a `data class` of its own (its own frame and value
 * semantics), and the [EnumType] records the ordered variant set so `when` can
 * check exhaustiveness and dispatch on the runtime discriminant. The enum type
 * is registered *before* its variants are parsed, so a variant field may refer
 * to the enum recursively (`Add(Expr l, Expr r)`).
 *
 * Variants are separated by a newline (automatic `;`) or an explicit comma; a
 * variant with no payload is a zero-field data class (`None`).
 */
class EnumParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val enumName = parser.context.declareName(TypeParser.parseVarname(parser))
        val enumType = EnumType(enumName)
        parser.context.registerEnum(enumName, enumType)

        parser.consume(TokenType.PUNCTUATION, '{')
        val variantNodes = mutableListOf<Node>()
        while (!parser.match(TokenType.PUNCTUATION, '}')) {
            // Skip separators between variants (newline -> ';', or an explicit ',').
            if (parser.match(TokenType.PUNCTUATION, ';')) { parser.consume(TokenType.PUNCTUATION, ';'); continue }
            if (parser.match(TokenType.PUNCTUATION, ',')) { parser.consume(TokenType.PUNCTUATION, ','); continue }

            val variantName = parser.context.declareName(TypeParser.parseVarname(parser))
            val defs = if (parser.match(TokenType.PUNCTUATION, '(')) {
                parser.parseDelimited('(', ')', ',') { TypeParser.parseDef(parser) }.map { it as DefNode }
            } else {
                emptyList()
            }
            parser.context.registerClass(variantName, ClassType(name = variantName, isData = true))
            enumType.variants.add(variantName)
            enumType.variantFields[variantName] = defs
            variantNodes.add(ClassNode(name = variantName, isData = true, defs = defs, body = VoidNode))
        }
        parser.consume(TokenType.PUNCTUATION, '}')

        // An enum has no runtime frame of its own; it compiles to its variants.
        return ProgramNode(prog = variantNodes)
    }
}
