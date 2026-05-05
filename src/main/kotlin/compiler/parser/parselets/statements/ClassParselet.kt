package compiler.parser.parselets.statements

import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DefNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

/**
 * Parses a `class X(...) {...}` declaration.
 *
 * The marker passed via [token].value selects the modifier: `"native"`
 * produces a native class, `"actor"` an actor class, anything else a plain
 * class. This is the same trick [NativeParselet] uses to share the body
 * parser; [ActorParselet] does the equivalent for `actor`.
 */
class ClassParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val native = token.value == "native"
        val isActor = token.value == "actor"
        val className = TypeParser.parseVarname(parser)
        val typeParams = parseTypeParams(parser)
        val savedGenerics = parser.context.saveGenericTypes()
        typeParams.forEach { parser.context.registerGenericType(it) }
        parser.context.registerClass(
            className,
            ClassType(name = className, isActor = isActor, typeParams = typeParams),
        )
        val defsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val defs = defsNodes.map { it as DefNode }
        val bodyList = parser.parseDelimited('{', '}', ';') {
            parser.parseExpression()
        }
        val body = when (bodyList.size) {
            0 -> VoidNode
            1 -> bodyList[0]
            else -> ProgramNode(prog = bodyList)
        }
        parser.context.restoreGenericTypes(savedGenerics)
        return ClassNode(
            name = className,
            native = native,
            isActor = isActor,
            typeParams = typeParams,
            defs = defs,
            body = body,
        )
    }
}

fun parseTypeParams(parser: ExpressionParser): List<String> {
    if (!parser.match(TokenType.PUNCTUATION, '[')) return emptyList()
    val params = mutableListOf<String>()
    parser.consume(TokenType.PUNCTUATION, '[')
    var first = true
    while (!parser.eof()) {
        if (parser.match(TokenType.PUNCTUATION, ']')) break
        if (first) first = false else parser.consume(TokenType.PUNCTUATION, ',')
        if (parser.match(TokenType.PUNCTUATION, ']')) break
        params.add(TypeParser.parseVarname(parser))
    }
    parser.consume(TokenType.PUNCTUATION, ']')
    return params
}
