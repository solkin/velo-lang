package compiler.parser.parselets

import compiler.nodes.*
import compiler.parser.*
import compiler.parser.parselets.literals.VariableParselet

object TypeParser {
    fun isDefType(parser: ExpressionParser): Boolean {
        val token = parser.peek() ?: return false
        return when (token.type) {
            TokenType.KEYWORD -> {
                val value = token.value as? String
                value != null && stdTypesSet.contains(value)
            }
            TokenType.VARIABLE -> {
                val value = token.value as? String
                value != null && parser.context.isClassType(value)
            }
            else -> false
        }
    }

    fun parseDefType(parser: ExpressionParser): Type {
        val token = parser.peek() ?: throw ParseException("Expected type but got end of input")
        if (!isDefType(parser)) {
            parser.peek()?.let { parser.consume(it.type) }
            throw ParseException("Expected def type, but got $token")
        }
        parser.consume(token.type)
        return parseType(parser, token)
    }

    fun parseType(parser: ExpressionParser, token: Token): Type {
        return when (val value = token.value) {
            BYTE -> ByteType
            INT -> IntType
            FLOAT -> FloatType
            STR -> StringType
            BOOL -> BoolType
            TUPLE -> {
                val derived = parseDerivedTypes(parser)
                TupleType(types = derived)
            }
            ARRAY -> {
                val derived = parseDerivedTypes(parser, count = 1)
                ArrayType(derived.first())
            }
            DICT -> {
                val types = parseDerivedTypes(parser, count = 2, separator = ':')
                DictType(TupleType(types))
            }
            FUNC -> {
                val derived = parseDerivedTypes(parser, count = 1)
                FuncType(derived = derived.first())
            }
            PTR -> {
                val derived = parseDerivedTypes(parser, count = 1)
                PtrType(derived = derived.first())
            }
            VOID -> VoidType
            ANY -> AnyType
            else -> {
                val className = value as? String
                    ?: throw IllegalArgumentException("Unknown type value: $value")
                parser.context.getClassType(className)
                    ?: throw IllegalArgumentException("Unknown type: $className")
            }
        }
    }

    fun parseDerivedTypes(
        parser: ExpressionParser,
        count: Int = -1,
        separator: Char = ','
    ): List<Type> {
        if (!parser.match(TokenType.PUNCTUATION, '[')) {
            return emptyList()
        }
        val types = mutableListOf<Type>()
        parser.consume(TokenType.PUNCTUATION, '[')
        var first = true
        while (!parser.eof()) {
            if (parser.match(TokenType.PUNCTUATION, ']')) {
                break
            }
            if (first) {
                first = false
            } else {
                parser.consume(TokenType.PUNCTUATION, separator)
            }
            if (parser.match(TokenType.PUNCTUATION, ']')) {
                break
            }
            types.add(parseDefType(parser))
        }
        parser.consume(TokenType.PUNCTUATION, ']')
        if (count != -1 && types.size != count) {
            throw ParseException("Derived types count is ${types.size} but must be $count")
        }
        return types
    }

    fun parseVarname(parser: ExpressionParser): String {
        val token = parser.consume(TokenType.VARIABLE)
        return token.value as String
    }

    fun parseDef(parser: ExpressionParser): DefNode {
        val type = parseDefType(parser)
        return parseDefBody(parser, type)
    }

    fun parseDefBody(parser: ExpressionParser, type: Type): DefNode {
        val name = parseVarname(parser)
        val def: Node? = if (parser.match(TokenType.OPERATOR, "=")) {
            parser.consume(TokenType.OPERATOR, "=")
            parser.parseExpression()
        } else {
            null
        }
        return DefNode(name = name, type = type, def = def)
    }
}
