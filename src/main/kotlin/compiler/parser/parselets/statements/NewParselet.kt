package compiler.parser.parselets.statements

import compiler.nodes.*
import compiler.parser.TokenType
import compiler.parser.parselets.ExpressionParser
import compiler.parser.parselets.PrefixParselet
import compiler.parser.parselets.TypeParser
import compiler.parser.Token

class NewParselet : PrefixParselet {
    override fun parse(parser: ExpressionParser, token: Token): Node {
        val tok = parser.peek() ?: throw IllegalStateException("Unexpected end after 'new'")
        parser.consume(tok.type)
        return when (tok.value) {
            "array" -> parseArrayInit(parser)
            "tuple" -> parseTupleInit(parser)
            "dict" -> parseDictInit(parser)
            "ptr" -> parsePtrInit(parser)
            else -> {
                val className = tok.value as String
                val classType = parser.context.getClassType(className)
                val typeArgs = if (classType != null && classType.typeParams.isNotEmpty()
                    && parser.match(TokenType.PUNCTUATION, '[')
                ) {
                    TypeParser.parseDerivedTypes(parser, count = classType.typeParams.size)
                } else {
                    emptyList()
                }
                VarNode(className, typeArgs = typeArgs)
            }
        }
    }

    private fun parseArrayInit(parser: ExpressionParser): Node {
        val type = TypeParser.parseDerivedTypes(parser, count = 1).first()
        val constructTok = parser.peek()
        if (constructTok?.type != TokenType.PUNCTUATION) {
            parser.peek()?.let { parser.consume(it.type) }
            throw Exception("Invalid array initialization")
        }
        return when (constructTok.value) {
            '(' -> {
                val length = parser.parseDelimited('(', ')', ',') {
                    parser.parseExpression()
                }
                if (length.isEmpty()) {
                    parser.peek()?.let { parser.consume(it.type) }
                    throw IllegalArgumentException("Array length expected")
                }
                ArrayNode(
                    listOf = null,
                    length = length[0],
                    type = type,
                )
            }
            '{' -> {
                val elements = parser.parseDelimited('{', '}', ',') {
                    parser.parseExpression()
                }
                ArrayNode(
                    listOf = elements,
                    length = null,
                    type = type,
                )
            }
            else -> {
                parser.peek()?.let { parser.consume(it.type) }
                throw Exception("Array must be initialized with size or values")
            }
        }
    }

    private fun parseDictInit(parser: ExpressionParser): Node {
        val types = TypeParser.parseDerivedTypes(parser, count = 2, separator = ':')
        val pairs = mutableListOf<Pair<Node, Node>>()
        parser.consume(compiler.parser.TokenType.PUNCTUATION, '{')
        var first = true
        while (!parser.eof()) {
            if (parser.match(compiler.parser.TokenType.PUNCTUATION, '}')) {
                break
            }
            if (first) {
                first = false
            } else {
                parser.consume(compiler.parser.TokenType.PUNCTUATION, ',')
            }
            if (parser.match(compiler.parser.TokenType.PUNCTUATION, '}')) {
                break
            }
            pairs.add(parseDictPair(parser))
        }
        parser.consume(compiler.parser.TokenType.PUNCTUATION, '}')
        val dictOf = pairs.associate { it.first to it.second }
        return DictNode(
            dictOf = dictOf,
            keyType = types.first(),
            valType = types.last(),
        )
    }

    private fun parseDictPair(parser: ExpressionParser): Pair<Node, Node> {
        val elements = parser.parseDelimited(':') {
            parser.parseExpression()
        }
        if (elements.size != 2) {
            parser.peek()?.let { parser.consume(it.type) }
            throw IllegalArgumentException("Pair must contain exactly two elements, but contains: ${elements.size}")
        }
        return Pair(elements[0], elements[1])
    }

    private fun parseTupleInit(parser: ExpressionParser): Node {
        val entries = parser.parseDelimited('(', ')', ',') {
            parser.parseExpression()
        }
        if (entries.isEmpty()) {
            parser.peek()?.let { parser.consume(it.type) }
            throw IllegalArgumentException("Tuple must contain one or more entries")
        }
        return TupleNode(entries)
    }

    private fun parsePtrInit(parser: ExpressionParser): Node {
        val derivedType = TypeParser.parseDerivedTypes(parser, count = 1).first()
        val constructTok = parser.peek()
        return when {
            constructTok?.type == TokenType.PUNCTUATION && constructTok.value == '(' -> {
                val value = parser.parseDelimited('(', ')', ',') {
                    parser.parseExpression()
                }
                if (value.isEmpty()) {
                    parser.peek()?.let { parser.consume(it.type) }
                    throw IllegalArgumentException("Pointer value expected")
                }
                PtrNode(
                    initialValue = value[0],
                    derivedType = derivedType,
                )
            }
            else -> {
                PtrNode(
                    initialValue = null,
                    derivedType = derivedType,
                )
            }
        }
    }
}
