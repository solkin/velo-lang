package parser

import nodes.*

class Parser(private val stream: TokenStream) {

    private val precedence = mapOf(
        "=" to 1,
        "||" to 2,
        "&&" to 3,
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
        if (isPunc(ch) != null) stream.next() else stream.croak("Expecting punctuation: \"$ch\"");
    }

    private fun isKw(kw: String?): Token? {
        return stream.peek()?.takeIf { tok ->
            tok.type == TokenType.KEYWORD && (kw == null || tok.value == kw)
        }
    }

    private fun skipKw(kw: String?) {
        if (isKw(kw) != null) stream.next() else stream.croak("Expecting keyword: \"$kw\"");
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
                val nextRight = maybeBinary(parseAtom(), hisPrec)
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

    private fun maybeCall(func: () -> Node): Node {
        val expr = func()
        return if (isPunc('(') != null) parseCall(expr) else expr
    }

    private fun maybeIndex(func: () -> Node): Node {
        val expr = func()
        return if (isPunc('[') != null) parseIndex(expr) else expr
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
        val expr = parseExpression()
        return WhileNode(
            cond = cond,
            expr = expr,
        )
    }

    private fun parseList(): Node {
        skipKw("list")
        val elements = delimited('(', ')', ',', ::parseExpression)
        return ListNode(
            listOf = elements
        )
    }

    private fun parseLambda(): Node {
        return LambdaNode(
            name = stream.peek()?.takeIf { tok ->
                tok.type == TokenType.VARIABLE
            }?.let { stream.next()?.value as? String },
            vars = delimited('(', ')', ',', ::parseVarname),
            body = parseExpression()
        )
    }

    private fun parseLet(): Node {
        skipKw("let")
        if (stream.peek()?.type == TokenType.VARIABLE) {
            val name = stream.next()?.value as? String
            val defs = delimited('(', ')', ',', ::parseVardef)
            return CallNode(
                func = LambdaNode(
                    name = name,
                    vars = defs.map { it.name },
                    body = parseExpression(),
                ),
                args = defs.map { it.def ?: FALSE }
            )
        }
        return LetNode(
            vars = delimited('(', ')', ',', ::parseVardef),
            body = parseExpression(),
        )
    }

    private fun parseVardef(): VardefNode {
        val name = parseVarname()
        val def: Node? = if (isOp("=") != null) {
            stream.next()
            parseExpression()
        } else null
        return VardefNode(name = name, def = def)
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

    private fun parseProg(): Node {
        val prog = delimited('{', '}', ';', ::parseExpression)
        return when (prog.size) {
            0 -> FALSE
            1 -> prog[0]
            else -> ProgramNode(prog = prog)
        }
    }

    private fun parseExpression(): Node {
        return maybeCall(
            fun(): Node {
                return maybeBinary(parseAtom(), 0)
            }
        )
    }

    private fun parseAtom(): Node {
        return maybeIndex(
            fun(): Node {
                return maybeCall(fun(): Node {
                    if (isPunc('(') != null) return inner('(', ')', ::parseExpression) ?: throw IllegalStateException()
                    if (isPunc('{') != null) return parseProg()
                    if (isKw("let") != null) return parseLet()
                    if (isKw("if") != null) return parseIf()
                    if (isKw("while") != null) return parseWhile()
                    if (isKw("list") != null) return parseList()
                    if (isKw("true") != null || isKw("false") != null) return parseBool()
                    if (isKw("lambda") != null || isKw("??") != null) {
                        stream.next()
                        return parseLambda()
                    }
                    val tok = stream.next()
                    return when (tok?.type) {
                        TokenType.VARIABLE -> VarNode(tok.value as String)
                        TokenType.NUMBER -> NumNode(tok.value.toString().toDouble())
                        TokenType.STRING -> StrNode(tok.value as String)
                        else -> {
                            stream.croak("Unexpected token: " + stream.peek().toString())
                            throw IllegalArgumentException()
                        }
                    }
                })
            })
    }

    fun parse(): Node {
        val prog = ArrayList<Node>()
        while (!stream.eof()) {
            prog.add(parseExpression());
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
        skipPunc(start);
        while (!stream.eof()) {
            if (isPunc(stop) != null) break
            if (first) first = false; else skipPunc(separator)
            if (isPunc(stop) != null) break
            a.add(parser())
        }
        skipPunc(stop);
        return a
    }

}
