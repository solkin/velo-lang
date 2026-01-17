package compiler.parser.parselets.statements

import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DefNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.VoidNode
import compiler.parser.Token
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser

class ClassParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val native = token.value == "native"
        val className = TypeParser.parseVarname(parser)
        parser.context.registerClass(className, ClassType(name = className))
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
        return ClassNode(
            name = className,
            native = native,
            defs = defs,
            body = body
        )
    }
}
