package compiler.parser.parselets.statements

import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DefNode
import compiler.nodes.InterfaceType
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.ParseException
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

/**
 * Parses a `class X(...) {...}` declaration.
 *
 * The marker passed via [token].value selects the modifier: `"actor"`
 * produces an actor class ([ActorParselet] shares this body parser),
 * anything else a plain class.
 */
class ClassParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val isActor = token.value == "actor"
        val isData = token.value == "data"
        val className = TypeParser.parseVarname(parser)
        val typeParamList = parseTypeParams(parser)
        val typeParams = typeParamList.map { it.name }
        val savedGenerics = parser.context.saveGenericTypes()
        typeParamList.forEach { parser.context.registerGenericType(it.name, it.bound) }
        parser.context.registerClass(
            className,
            ClassType(name = className, isActor = isActor, isData = isData, typeParams = typeParams),
        )
        val defsNodes = parser.parseDelimited('(', ')', ',') {
            TypeParser.parseDef(parser)
        }
        val defs = defsNodes.map { it as DefNode }
        // Optional explicit conformance: `class Foo(...) : View, Clickable {...}`.
        // Structural satisfaction makes this redundant, but declaring it gives a
        // checked, documented intent (and a precise error when a method is missing).
        val conforms = mutableListOf<InterfaceType>()
        if (parser.match(TokenType.PUNCTUATION, ':')) {
            parser.consume(TokenType.PUNCTUATION, ':')
            while (true) {
                val conform = TypeParser.parseDefType(parser)
                if (conform !is InterfaceType) {
                    throw ParseException("Class '$className' can only conform to interfaces, not '${conform.log()}'")
                }
                conforms.add(conform)
                if (parser.match(TokenType.PUNCTUATION, ',')) {
                    parser.consume(TokenType.PUNCTUATION, ',')
                } else {
                    break
                }
            }
        }
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
            isActor = isActor,
            isData = isData,
            typeParams = typeParams,
            defs = defs,
            body = body,
            conforms = conforms,
            typeParamBounds = typeParamList.map { it.bound },
        )
    }
}

/** A generic type parameter and its optional interface bound (`T` or `T: View`). */
data class TypeParam(val name: String, val bound: InterfaceType?)

fun parseTypeParams(parser: ExpressionParser): List<TypeParam> {
    if (!parser.match(TokenType.PUNCTUATION, '[')) return emptyList()
    val params = mutableListOf<TypeParam>()
    parser.consume(TokenType.PUNCTUATION, '[')
    var first = true
    while (!parser.eof()) {
        if (parser.match(TokenType.PUNCTUATION, ']')) break
        if (first) first = false else parser.consume(TokenType.PUNCTUATION, ',')
        if (parser.match(TokenType.PUNCTUATION, ']')) break
        val paramName = TypeParser.parseVarname(parser)
        val bound = if (parser.match(TokenType.PUNCTUATION, ':')) {
            parser.consume(TokenType.PUNCTUATION, ':')
            val boundType = TypeParser.parseDefType(parser)
            boundType as? InterfaceType
                ?: throw ParseException("Type parameter '$paramName' bound must be an interface, got '${boundType.log()}'")
        } else {
            null
        }
        params.add(TypeParam(paramName, bound))
    }
    parser.consume(TokenType.PUNCTUATION, ']')
    return params
}
