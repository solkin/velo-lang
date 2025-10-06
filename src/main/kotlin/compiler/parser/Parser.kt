package compiler.parser

import compiler.nodes.AssignNode
import compiler.nodes.AnyType
import compiler.nodes.BinaryNode
import compiler.nodes.BoolNode
import compiler.nodes.CallNode
import compiler.nodes.DefNode
import compiler.nodes.FloatNode
import compiler.nodes.FuncNode
import compiler.nodes.IfNode
import compiler.nodes.IndexNode
import compiler.nodes.IntNode
import compiler.nodes.LetNode
import compiler.nodes.ArrayNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.PropNode
import compiler.nodes.StringNode
import compiler.nodes.VarNode
import compiler.nodes.TupleNode
import compiler.nodes.BoolType
import compiler.nodes.ByteType
import compiler.nodes.FloatType
import compiler.nodes.FuncType
import compiler.nodes.IntType
import compiler.nodes.TupleType
import compiler.nodes.ArrayType
import compiler.nodes.ByteNode
import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DictNode
import compiler.nodes.DictType
import compiler.nodes.ScopeNode
import compiler.nodes.StringType
import compiler.nodes.Type
import compiler.nodes.VoidType
import compiler.nodes.VoidNode
import compiler.nodes.WhileNode

class Parser(private val stream: TokenStream, private val depLoader: DependencyLoader) {

    private val precedence = mapOf(
        "=" to 1,
        "|" to 2,
        "&" to 3,
        "^" to 4,
        "<" to 7, ">" to 7, "<=" to 7, ">=" to 7, "==" to 7, "!=" to 7,
        "+" to 10, "-" to 10,
        "*" to 20, "/" to 20, "%" to 20,
    )

    private fun isPunc(ch: Char?): Token? {
        return stream.peek()?.takeIf { tok ->
            tok.type == TokenType.PUNCTUATION && (ch == null || tok.value == ch)
        }
    }

    private fun skipPunc(ch: Char) {
        if (isPunc(ch) != null) stream.next() else stream.croak("Expecting punctuation: \"$ch\"")
    }

    private fun isKw(kw: String?): Token? {
        return stream.peek()?.takeIf { tok ->
            tok.type == TokenType.KEYWORD && (kw == null || tok.value == kw)
        }
    }

    private fun skipKw(kw: String?) {
        if (isKw(kw) != null) stream.next() else stream.croak("Expecting keyword: \"$kw\"")
    }

    private fun isDef(): Token? {
        return stream.peek()?.takeIf { tok ->
            when (tok.type) {
                TokenType.KEYWORD -> stdTypesSet.plus(stream.classTypesMap.keys).contains(tok.value)
                else -> false
            }
        }
    }

    private fun parseDefType(): Type {
        val tok = isDef()
        if (tok != null) {
            stream.next()
        } else {
            stream.croak("Expecting def type, but got $tok")
            throw IllegalArgumentException()
        }
        return parseType(tok)
    }

    private fun parseType(tok: Token): Type {
        return when (tok.value) {
            BYTE -> ByteType
            INT -> IntType
            FLOAT -> FloatType
            STR -> StringType
            BOOL -> BoolType
            TUPLE -> {
                val derived = parseDerivedTypes()
                TupleType(types = derived)
            }

            ARRAY -> ArrayType(parseDerivedTypes(count = 1).first())
            DICT -> {
                val types = parseDerivedTypes(count = 2, separator = ':')
                DictType(TupleType(types))
            }

            FUNC -> FuncType(derived = parseDerivedTypes(count = 1).first())
            VOID -> VoidType
            ANY -> AnyType
            else -> {
                stream.classTypesMap[tok.value] ?: throw IllegalArgumentException("Unknown type ${tok.value}")
            }
        }
    }

    private fun maybeDef(native: Boolean): Node {
        val tok = isDef()
        if (tok != null) {
            stream.next()
        } else {
            stream.croak("Definition expected: class, function or variable")
            throw IllegalArgumentException()
        }
        val nextTokType = stream.peek()?.type
        return when {
            tok.value == CLASS && nextTokType == TokenType.VARIABLE -> parseClass(native)
            tok.value == FUNC && (nextTokType == TokenType.VARIABLE || isPunc('(') != null) -> parseFunc(native) // Start of named or anonymous function definition
            else -> parseDefBody(type = parseType(tok))
        }
    }

    private fun parseDef(): DefNode {
        val type = parseDefType()
        return parseDefBody(type)
    }

    private fun parseDefBody(type: Type): DefNode {
        val name = parseVarname()
        val def: Node? = if (isOp("=") != null) {
            skipOp("=")
            parseExpression()
        } else null
        return DefNode(name = name, type = type, def = def)
    }

    private fun parseDerivedTypes(count: Int = -1, separator: Char = ','): List<Type> {
        val ders = isPunc('[')
            ?.let { delimited('[', ']', separator, ::parseDefType) }
            ?: emptyList()
        return ders.takeIf { it.size == count || count == -1 } ?: run {
            stream.croak("Derived types count is ${ders.size} but must be $count")
            throw IllegalArgumentException()
        }
    }

    private fun isOp(op: String? = null): Token? {
        return stream.peek()?.takeIf { tok ->
            tok.type == TokenType.OPERATOR && (op == null || tok.value == op)
        }
    }

    @Suppress("SameParameterValue")
    private fun skipOp(op: String?) {
        if (isOp(op) != null) stream.next() else stream.croak("Expecting operator: \"$op\"")
    }

    private fun maybeBinary(left: Node, myPrec: Int): Node {
        val tok = isOp()
        if (tok != null) {
            val hisPrec = precedence[tok.value]
            if (hisPrec == null) {
                stream.croak("Unexpected operator: \"$tok.value\"")
                throw IllegalArgumentException()
            }
            if (hisPrec > myPrec) {
                stream.next()
                val nextRight = maybeBinary(maybePostfix(parseAtom()), hisPrec)
                val nextLeft = when (tok.value) {
                    "=" -> AssignNode(left = left, right = nextRight)
                    else -> BinaryNode(operator = tok.value as String, left = left, right = nextRight)
                }
                return maybeBinary(
                    left = nextLeft,
                    myPrec = myPrec
                )
            }
        }
        return left
    }

    private fun maybeCall(expr: Node): Node {
        return if (isPunc('(') != null) parseCall(expr) else expr
    }

    private fun maybeIndex(expr: Node): Node {
        return if (isPunc('[') != null) parseIndex(expr) else expr
    }

    private fun maybeProp(expr: Node): Node {
        return if (isPunc('.') != null) parseProp(expr) else expr
    }

    private fun parseIf(): Node {
        skipKw("if")
        val condNode = parseExpression()
        if (isPunc('{') == null) skipKw("then")
        val thenNode = parseExpression()
        val elseNode = isKw("else")?.let {
            stream.next()
            parseExpression()
        }
        return IfNode(condNode, thenNode, elseNode)
    }

    private fun parseWhile(): Node {
        skipKw("while")
        val cond = parseExpression()
        val expr = parseProg()
        return WhileNode(
            cond = cond,
            expr = ScopeNode(expr),
        )
    }

    private fun parseArrayOf(): Node {
        skipKw("arrayOf")
        val type = parseDerivedTypes(count = 1).first()
        val elements = delimited('(', ')', ',', ::parseExpression)
        return ArrayNode(
            listOf = elements,
            type = type,
        )
    }

    private fun parseDictOf(): Node {
        skipKw("dictOf")
        val types = parseDerivedTypes(count = 2, separator = ':')
        val elements = delimited('(', ')', ',', ::parseDictPair)
        return DictNode(
            dictOf = elements.toMap(),
            keyType = types.first(),
            valType = types.last(),
        )
    }

    private fun parseDictPair(): Pair<Node, Node> {
        val elements = delimited(separator = ':', ::parseExpression)
        if (elements.size != 2) {
            stream.croak("Pair must contain exactly two elements, but contains: ${elements.size}")
            throw IllegalArgumentException()
        }
        return Pair(
            first = elements[0],
            second = elements[1],
        )
    }

    private fun parseTuple(): Node {
        skipKw("tupleOf")
        val types = parseDerivedTypes()
        if (types.isEmpty()) {
            stream.croak("Tuple requires derived types definition")
            throw IllegalArgumentException()
        }
        val entries = delimited('(', ')', ',', ::parseExpression)
        if (entries.isEmpty()) {
            stream.croak("Tuple must contain one or more entries")
            throw IllegalArgumentException()
        }
        return TupleNode(
            entries
        )
    }

    private fun parseNative(): Node {
        skipKw("native")
        return maybeDef(native = true)
    }

    private fun parseNew(): Node {
        skipKw("new")
        val tok = stream.next() ?: throw IllegalStateException()
        // Pass class type as VarNode to parse call in a common way
        return maybePostfix(VarNode(tok.value as String))
    }

    private fun parseInclude(): Node {
        skipKw("include")
        val path = stream.next()?.takeIf { tok ->
            tok.type == TokenType.STRING
        } ?: throw IllegalStateException("Include keyword must end with a relative file path")
        skipPunc(';')
        depLoader.load(name = path.value as String)
        return parseAtom()
    }

    private fun parseClass(native: Boolean): Node {
        val className = parseVarname()
        stream.classTypesMap[className] = ClassType(name = className)
        return ClassNode(
            name = className,
            native = native,
            defs = delimited('(', ')', ',', ::parseDef),
            body = parseProg(),
        )
    }

    private fun parseFunc(native: Boolean): Node {
        return FuncNode(
            name = stream.peek()?.takeIf { tok ->
                tok.type == TokenType.VARIABLE
            }?.let { stream.next()?.value as? String },
            native = native,
            defs = delimited('(', ')', ',', ::parseDef),
            type = parseDefType(),
            body = if (native) VoidNode else parseProg(),
        )
    }

    private fun parseLet(): Node {
        skipKw("let")
        if (stream.peek()?.type == TokenType.VARIABLE) {
            val name = stream.next()?.value as? String
            val defs = delimited('(', ')', ',', ::parseDef)
            val type = parseDefType()
            return CallNode(
                func = FuncNode(
                    name = name,
                    native = false,
                    defs = defs,
                    type = type,
                    body = parseExpression(),
                ),
                args = defs.map { it.def ?: VoidNode }
            )
        }
        return ScopeNode(
            LetNode(
                vars = delimited('(', ')', ',', ::parseDef),
                body = parseExpression(),
            )
        )
    }

    private fun parseExt(): Node {
        skipKw("ext")
        val self = delimited(start = '(', stop = ')', separator = ',', parser = ::parseDef)
        if (self.size != 1) {
            stream.croak("Self type and variable must be defined for extension function")
        }
        val name = stream.peek()?.takeIf { tok ->
            tok.type == TokenType.VARIABLE
        }?.let { stream.next()?.value as? String }
        return FuncNode(
            name = self.first().type.name() + "@" + name,
            native = false,
            defs = self + delimited('(', ')', ',', ::parseDef),
            type = parseDefType(),
            body = parseProg(),
        )
    }

    private fun parseBool(): Node {
        return BoolNode(
            value = stream.next()?.value == "true"
        )
    }

    private fun parseVarname(): String {
        val name = stream.next()
        if (name == null || name.type != TokenType.VARIABLE) {
            stream.croak("Expecting variable name")
            throw IllegalArgumentException()
        }
        return name.value as String
    }

    private fun parseCall(node: Node): Node {
        return CallNode(
            func = node,
            args = delimited('(', ')', ',', ::parseExpression)
        )
    }

    private fun parseIndex(node: Node): Node {
        val index = inner('[', ']', ::parseExpression)
        if (index == null) {
            stream.croak("Expecting an index element: " + stream.peek().toString())
            throw IllegalArgumentException()
        }
        return IndexNode(
            list = node,
            index = index,
        )
    }

    private fun parseProp(node: Node): Node {
        skipPunc('.')

        val tok = stream.peek()
        if (tok?.type == TokenType.VARIABLE || tok?.type == TokenType.KEYWORD || tok?.type == TokenType.NUMBER) {
            val tokVal = stream.next()?.value
            val name = when (tokVal) {
                is String -> tokVal
                is Int -> tokVal.toString()
                else -> {
                    stream.croak("Invalid property type $tokVal")
                    throw IllegalArgumentException()
                }
            }
            if (name.isEmpty()) {
                stream.croak("Property can not be empty")
                throw IllegalArgumentException()
            }
            val args: List<Node>? = when (isPunc('(')) {
                null -> null
                else -> delimited('(', ')', ',', ::parseExpression)
            }
            return PropNode(
                name = name,
                args = args,
                parent = node
            )
        }
        stream.croak("Invalid property syntax")
        throw IllegalArgumentException()
    }

    private fun parseProg(): Node {
        val prog = delimited('{', '}', ';', ::parseExpression)
        return when (prog.size) {
            0 -> VoidNode
            1 -> prog[0]
            else -> ProgramNode(prog = prog)
        }
    }

    private fun parseExpression(): Node {
        val atom = parseAtom()
        val postfix = maybePostfix(atom)
        val binary = maybeBinary(postfix, 0)
        return maybePostfix(binary)
    }

    private fun maybePostfix(expr: Node): Node {
        val call = maybeCall(expr)
        val index = maybeIndex(call)
        val prop = maybeProp(index)
        return if (prop != expr) {
            maybePostfix(prop)
        } else {
            prop
        }
    }

    private fun parseAtom(): Node {
        if (isKw("include") != null) return parseInclude()
        if (isPunc('(') != null) return inner('(', ')', ::parseExpression)
            ?: throw IllegalStateException()
        if (isPunc('{') != null) return parseProg()
        if (isDef() != null) return maybeDef(native = false)
        if (isKw("let") != null) return parseLet()
        if (isKw("ext") != null) return parseExt()
        if (isKw("if") != null) return parseIf()
        if (isKw("while") != null) return parseWhile()
        if (isKw("arrayOf") != null) return parseArrayOf()
        if (isKw("dictOf") != null) return parseDictOf()
        if (isKw("tupleOf") != null) return parseTuple()
        if (isKw("true") != null || isKw("false") != null) return parseBool()
        if (isKw("native") != null) return parseNative()
        if (isKw("new") != null) return parseNew()
        val tok = stream.next()
        return when (tok?.type) {
            TokenType.VARIABLE -> VarNode(tok.value as String)
            TokenType.NUMBER -> when (tok.value) {
                is Byte -> ByteNode(tok.value)
                is Int -> IntNode(tok.value)
                is Float -> FloatNode(tok.value)
                is Double -> FloatNode(tok.value.toFloat())
                else -> {
                    stream.croak("Unexpected number format: " + tok.value::class.java)
                    throw IllegalArgumentException()
                }
            }

            TokenType.STRING -> StringNode(tok.value as String)
            else -> {
                stream.croak("Unexpected token: $tok")
                throw IllegalArgumentException()
            }
        }
    }

    fun parse(): Node {
        val prog = ArrayList<Node>()
        while (!stream.eof()) {
            prog.add(parseExpression())
            if (!stream.eof()) skipPunc(';')
        }
        return ProgramNode(prog = prog)
    }

    private fun <T> inner(start: Char, stop: Char, parser: () -> T): T? {
        if (isPunc(start) != null) {
            stream.next()
            val v = parser()
            skipPunc(stop)
            return v
        }
        return null
    }

    private fun <T> delimited(start: Char, stop: Char, separator: Char, parser: () -> T): List<T> {
        val a = ArrayList<T>()
        var first = true
        skipPunc(start)
        while (!stream.eof()) {
            if (isPunc(stop) != null) break
            if (first) first = false; else skipPunc(separator)
            if (isPunc(stop) != null) break
            a.add(parser())
        }
        skipPunc(stop)
        return a
    }

    @Suppress("SameParameterValue")
    private fun <T> delimited(separator: Char, parser: () -> T): List<T> {
        val a = ArrayList<T>()
        var first = true
        while (!stream.eof()) {
            if (first) {
                first = false
            } else {
                if (isPunc(separator) != null) {
                    skipPunc(separator)
                } else {
                    break
                }
            }
            a.add(parser())
        }
        return a
    }

}
