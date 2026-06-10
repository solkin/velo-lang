package compiler.parser.parselets.literals

import compiler.nodes.ByteNode
import compiler.nodes.FloatNode
import compiler.nodes.IntNode
import compiler.nodes.Node
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.Token

class NumberParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        return when (val value = token.value) {
            is Byte -> ByteNode(value)
            is Int -> IntNode(value)
            is Float -> FloatNode(value)
            else -> {
                parser.peek()?.let { parser.consume(it.type) }
                throw IllegalArgumentException("Unexpected number format: ${value::class.java}")
            }
        }
    }
}
