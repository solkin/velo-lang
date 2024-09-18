package parser

import nodes.AssignNode
import nodes.BinaryNode
import nodes.BoolNode
import nodes.CallNode
import nodes.DefNode
import nodes.DoubleNode
import nodes.FuncNode
import nodes.IfNode
import nodes.IndexNode
import nodes.IntNode
import nodes.LetNode
import nodes.ListNode
import nodes.Node
import nodes.ProgramNode
import nodes.PropNode
import nodes.StrNode
import nodes.StructNode
import nodes.SubjectNode
import nodes.TreeNode
import nodes.VarNode
import nodes.DataType
import nodes.PairNode
import nodes.VMBoolean
import nodes.VMByte
import nodes.VMFloat
import nodes.VMFunction
import nodes.VMInt
import nodes.VMPair
import nodes.VMSlice
import nodes.VMString
import nodes.VMType
import nodes.VMVoid
import nodes.VoidNode
import nodes.WhileNode
import nodes.getDefault
import nodes.getDefaultNode
import nodes.mask
import nodes.unmask

class Parser(private val stream: TokenStream) {

    private val precedence = mapOf(
        "=" to 1,
        "||" to 2,
        "&&" to 3,
        "<" to 7, ">" to 7, "<=" to 7, ">=" to 7, "==" to 7, "!=" to 7,
        "+" to 10, "-" to 10,
        "*" to 20, "/" to 20, "%" to 20,
    )
    private val types = DataType.values().map { it.type }

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
            tok.type == TokenType.KEYWORD && types.find { kw -> tok.value == kw } != null
        }
    }

    private fun parseDerivedTypes(count: Int): List<VMType> {
        val ders = isPunc('[')
            ?.let { delimited('[', ']', ',', ::parseDefType) }
            ?: emptyList()
        return ders.takeIf { it.size == count } ?: run {
            stream.croak("Derived types count is ${ders.size} but must be $count")
            throw IllegalArgumentException()
        }
    }

    private fun parseDefType(): VMType {
        val tok = isDef()
        if (tok != null) {
            stream.next()
        } else {
            stream.croak("Expecting def type one of: \"$types\"")
            throw IllegalArgumentException()
        }
        val dataType = DataType.values().first { kw ->
            tok.value == kw.type
        }
        val type = when (dataType) {
            DataType.BYTE -> VMByte
            DataType.INT -> VMInt
            DataType.FLOAT -> VMFloat
            DataType.STRING -> VMString
            DataType.BOOLEAN -> VMBoolean
            DataType.PAIR -> {
                val derived = parseDerivedTypes(count = 2)
                VMPair(first = derived[0], second = derived[1])
            }
            DataType.SLICE -> VMSlice(parseDerivedTypes(count = 1).first())
            DataType.FUNCTION -> VMFunction(parseDerivedTypes(count = 1).first())
            DataType.VOID -> VMVoid
        }
        return type
    }

    private fun isOp(op: String? = null): Token? {
        return stream.peek()?.takeIf { tok ->
            tok.type == TokenType.OPERATOR && (op == null || tok.value == op)
        }
    }

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
        val cond = parseExpression()
        if (isPunc('{') == null) skipKw("then")
        val then = parseExpression()
        val elseNode = isKw("else")?.let {
            stream.next()
            parseExpression()
        }
        return IfNode(
            condNode = cond,
            thenNode = then,
            elseNode = elseNode
        )
    }

    private fun parseWhile(): Node {
        skipKw("while")
        val cond = parseExpression()
        val expr = parseProg()
        return WhileNode(
            cond = cond,
            expr = expr,
        )
    }

    private fun parseSlice(): Node {
        skipKw("sliceOf")
        val type = parseDerivedTypes(count = 1).first()
        val elements = delimited('(', ')', ',', ::parseExpression)
        return ListNode(
            listOf = elements,
            type = type,
        )
    }

    private fun parsePair(): Node {
        skipKw("pairOf")
        val type = parseDerivedTypes(count = 2).first()
        val elements = delimited('(', ')', ',', ::parseExpression)
        if (elements.size != 2) {
            stream.croak("Pair must contain exactly two elements, but contains: ${elements.size}")
            throw IllegalArgumentException()
        }
        return PairNode(
            first = elements[0],
            second = elements[1],
        )
    }

    private fun parseTree(): Node {
        skipKw("tree")
        val elements = delimited('(', ')', ',', ::parseExpression)
        return TreeNode(
            treeOf = elements
        )
    }

    private fun parseStruct(): Node {
        skipKw("struct")
        val elements = delimited('(', ')', ',', ::parseDef)
        return StructNode(
            nodes = elements
        )
    }

    private fun parseSubject(): Node {
        skipKw("subject")
        val value = inner('(', ')', ::parseExpression)
        return SubjectNode(
            init = value
        )
    }

    private fun parseFunc(): Node {
        return FuncNode(
            name = stream.peek()?.takeIf { tok ->
                tok.type == TokenType.VARIABLE
            }?.let { stream.next()?.value as? String },
            defs = delimited('(', ')', ',', ::parseDef),
            type = parseDefType(),
            body = parseProg(),
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
                    defs = defs,
                    type = type,
                    body = parseExpression(),
                ),
                args = defs.map { it.def ?: it.type.type.getDefaultNode() }
            )
        }
        return LetNode(
            vars = delimited('(', ')', ',', ::parseDef),
            body = parseExpression(),
        )
    }

    private fun parseDef(): DefNode {
        val type = parseDefType()
        val name = parseVarname()
        val def: Node? = if (isOp("=") != null) {
            stream.next()
            parseExpression()
        } else null
        return DefNode(name = name, type = type, def = def)
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

        if (stream.peek()?.type == TokenType.VARIABLE) {
            val name = stream.next()?.value as? String
            if (name.isNullOrEmpty()) {
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
            0 -> VoidNode()
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
        if (isPunc('(') != null) return inner('(', ')', ::parseExpression)
            ?: throw IllegalStateException()
        if (isPunc('{') != null) return parseProg()
        if (isDef() != null) return parseDef()
        if (isKw("let") != null) return parseLet()
        if (isKw("if") != null) return parseIf()
        if (isKw("while") != null) return parseWhile()
        if (isKw("sliceOf") != null) return parseSlice()
        if (isKw("pairOf") != null) return parsePair()
        if (isKw("tree") != null) return parseTree()
        if (isKw("struct") != null) return parseStruct()
        if (isKw("subject") != null) return parseSubject()
        if (isKw("true") != null || isKw("false") != null) return parseBool()
        if (isKw("func") != null) {
            stream.next()
            return parseFunc()
        }
        val tok = stream.next()
        return when (tok?.type) {
            TokenType.VARIABLE -> VarNode(tok.value as String)
            TokenType.NUMBER -> when (tok.value) {
                is Double -> DoubleNode(tok.value)
                is Int -> IntNode(tok.value)
                else -> {
                    stream.croak("Unexpected number format: " + tok.value::class.java)
                    throw IllegalArgumentException()
                }
            }

            TokenType.STRING -> StrNode(tok.value as String)
            else -> {
                stream.croak("Unexpected token: " + stream.peek().toString())
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

}
