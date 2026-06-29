package compiler.parser.parselets.statements

import compiler.nodes.DefNode
import compiler.nodes.FuncType
import compiler.nodes.InterfaceNode
import compiler.nodes.InterfaceType
import compiler.nodes.Node
import compiler.parser.ParseException
import compiler.parser.Token
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

/**
 * Parses an `interface Name { func sig(...) Ret; ... }` declaration.
 *
 * The body is a list of method signatures — a `func` keyword, a name, a typed
 * parameter list and a return type, but no body. The [InterfaceType] is
 * registered with the parser *before* its methods are read (with a shared,
 * still-empty method map) so a signature may refer to the interface itself,
 * e.g. `func add(View child) View`.
 */
class InterfaceParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val name = TypeParser.parseVarname(parser)
        val type = InterfaceType(name)
        parser.context.registerInterface(name, type)

        parser.consume(TokenType.PUNCTUATION, '{')
        while (!parser.eof() && !parser.match(TokenType.PUNCTUATION, '}')) {
            parser.consume(TokenType.KEYWORD, "func")
            val methodName = parser.consume(TokenType.VARIABLE).value as String
            val defs = parser.parseDelimited('(', ')', ',') {
                TypeParser.parseDef(parser)
            }.map { it as DefNode }
            val returns = TypeParser.parseDefType(parser)
            if (type.methods.containsKey(methodName)) {
                throw ParseException("Interface '$name' declares method '$methodName' more than once")
            }
            type.methods[methodName] = FuncType(derived = returns, args = defs.map { it.type })
            if (parser.match(TokenType.PUNCTUATION, ';')) {
                parser.consume(TokenType.PUNCTUATION, ';')
            }
        }
        parser.consume(TokenType.PUNCTUATION, '}')

        return InterfaceNode(type)
    }
}
