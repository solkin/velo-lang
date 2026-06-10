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
                value != null && (parser.context.isClassType(value) || parser.context.isGenericType(value))
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
            FUNC -> parseFuncType(parser)
            PTR -> {
                val derived = parseDerivedTypes(parser, count = 1)
                PtrType(derived = derived.first())
            }
            VOID -> VoidType
            ANY -> AnyType
            ACTOR -> {
                // Syntax: actor[ClassName]. Parser-level we accept any single
                // class type wrapper; the actor-class invariant is enforced
                // by ActorBoundType's `init` block at compile time.
                val derived = parseDerivedTypes(parser, count = 1).first()
                require(derived is ClassType) {
                    "actor[T] requires a class type, got ${derived.log()}"
                }
                ActorBoundType(derived)
            }
            FUTURE -> {
                // Syntax: future[T]. Any type is accepted at the surface;
                // transferability rules (FutureType is non-transferable) are
                // enforced at actor-signature validation time, not here.
                val derived = parseDerivedTypes(parser, count = 1).first()
                FutureType(derived)
            }
            else -> {
                val className = value as? String
                    ?: throw IllegalArgumentException("Unknown type value: $value")
                parser.context.getGenericType(className)?.let { return it }
                val classType = parser.context.getClassType(className)
                    ?: throw IllegalArgumentException("Unknown type: $className")
                if (classType.typeParams.isNotEmpty() && parser.match(TokenType.PUNCTUATION, '[')) {
                    val typeArgs = parseDerivedTypes(parser, count = classType.typeParams.size)
                    classType.copy(typeArgs = typeArgs)
                } else {
                    classType
                }
            }
        }
    }

    /**
     * Two surface forms of a function type:
     *   - `func[T]`            — loose: only the return type is declared;
     *     arity and argument types are unchecked at call sites.
     *   - `func[(T1, T2) T]`   — full signature (use `()` for no args).
     *     Required for callbacks that cross an actor or native boundary.
     */
    private fun parseFuncType(parser: ExpressionParser): FuncType {
        parser.consume(TokenType.PUNCTUATION, '[')
        val type = if (parser.match(TokenType.PUNCTUATION, '(')) {
            parser.consume(TokenType.PUNCTUATION, '(')
            val argTypes = mutableListOf<Type>()
            var first = true
            while (!parser.eof() && !parser.match(TokenType.PUNCTUATION, ')')) {
                if (first) {
                    first = false
                } else {
                    parser.consume(TokenType.PUNCTUATION, ',')
                }
                argTypes.add(parseDefType(parser))
            }
            parser.consume(TokenType.PUNCTUATION, ')')
            FuncType(derived = parseDefType(parser), args = argTypes)
        } else {
            FuncType(derived = parseDefType(parser))
        }
        parser.consume(TokenType.PUNCTUATION, ']')
        return type
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
