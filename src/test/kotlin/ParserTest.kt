import compiler.nodes.AssignNode
import compiler.nodes.BinaryNode
import compiler.nodes.BoolNode
import compiler.nodes.BoolType
import compiler.nodes.CallNode
import compiler.nodes.DefNode
import compiler.nodes.FloatNode
import compiler.nodes.FuncNode
import compiler.nodes.IfNode
import compiler.nodes.IndexNode
import compiler.nodes.IntNode
import compiler.nodes.IntType
import compiler.nodes.LetNode
import compiler.nodes.Node
import compiler.nodes.ProgramNode
import compiler.nodes.ArrayNode
import compiler.nodes.ByteNode
import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DictNode
import compiler.nodes.PropNode
import compiler.nodes.ScopeNode
import compiler.nodes.StringNode
import compiler.nodes.StringType
import compiler.nodes.TupleNode
import compiler.nodes.TupleType
import compiler.nodes.VarNode
import compiler.nodes.VoidNode
import compiler.nodes.VoidType
import compiler.parser.InputStack
import compiler.parser.Parser
import compiler.parser.SimpleInput
import compiler.parser.StringInput
import compiler.parser.TokenStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserTest {

    @Test
    fun testParseFloatNum() {
        val parser = makeSimpleParser("123.5")

        val node = parser.parse()

        assertEquals(
            expected = FloatNode(
                value = 123.5f,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseFloatPostfixNum() {
        val parser = makeSimpleParser("123f")

        val node = parser.parse()

        assertEquals(
            expected = FloatNode(
                value = 123f,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseByteNum() {
        val parser = makeSimpleParser("123y")

        val node = parser.parse()

        assertEquals(
            expected = ByteNode(
                value = 123.toByte(),
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseIntNum() {
        val parser = makeSimpleParser("123")

        val node = parser.parse()

        assertEquals(
            expected = IntNode(
                value = 123,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseString() {
        val parser = makeSimpleParser("\"Hello World\"")

        val node = parser.parse()

        assertEquals(
            expected = StringNode(
                value = "Hello World"
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseBoolean() {
        val parser = makeSimpleParser("true")

        val node = parser.parse()

        assertEquals(
            expected = BoolNode(
                value = true
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseVoid() {
        val parser = makeSimpleParser("void")

        val node = parser.parse()

        assertEquals(
            expected = VoidNode.wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseVar() {
        val parser = makeSimpleParser("foo")

        val node = parser.parse()

        assertEquals(
            expected = VarNode(
                name = "foo"
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseAnonymousFunc() {
        val parser = makeSimpleParser("func(int a) bool { false }")

        val node = parser.parse()

        assertEquals(
            expected = FuncNode(
                name = null,
                defs = listOf(DefNode("a", IntType, def = null)),
                type = BoolType,
                body = BoolNode(value = false),
                native = false,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseNamedFunc() {
        val parser = makeSimpleParser("func A(int a) void { }")

        val node = parser.parse()

        assertEquals(
            expected = FuncNode(
                name = "A",
                defs = listOf(DefNode("a", IntType, def = null)),
                type = VoidType,
                body = VoidNode,
                native = false,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseExtensionFunc() {
        val parser = makeSimpleParser("ext(int i) A(int a) void { }")

        val node = parser.parse()

        assertEquals(
            expected = FuncNode(
                name = "int@A",
                defs = listOf(DefNode("i", IntType, def = null), DefNode("a", IntType, def = null)),
                type = VoidType,
                body = VoidNode,
                native = false,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseCall() {
        val parser = makeSimpleParser("println(\"Hello, World!\")")

        val node = parser.parse()

        assertEquals(
            expected = CallNode(
                func = VarNode(name = "println"),
                args = listOf(StringNode(value = "Hello, World!"))
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseIfThen() {
        val parser = makeSimpleParser("if (true) then false")

        val node = parser.parse()

        assertEquals(
            expected = IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = null
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseIfThenElse() {
        val parser = makeSimpleParser("if (true) then false else true")

        val node = parser.parse()

        assertEquals(
            expected = IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = BoolNode(value = true)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseIf() {
        val parser = makeSimpleParser("if (true) { false }")

        val node = parser.parse()

        assertEquals(
            expected = IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = null
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseElse() {
        val parser = makeSimpleParser("if (true) { false } else { true }")

        val node = parser.parse()

        assertEquals(
            expected = IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = BoolNode(value = true)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseAssign() {
        val parser = makeSimpleParser("a = 4")

        val node = parser.parse()

        assertEquals(
            expected = AssignNode(
                left = VarNode(name = "a"),
                right = IntNode(value = 4)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseArrayOf() {
        val parser = makeSimpleParser("a = arrayOf[int]()")

        val node = parser.parse()

        assertEquals(
            expected = AssignNode(
                left = VarNode(name = "a"),
                right = ArrayNode(listOf = emptyList(), IntType)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseArrayOfOf() {
        val parser = makeSimpleParser("a = arrayOf[int](1, 2, 5)")

        val node = parser.parse()

        assertEquals(
            expected = AssignNode(
                left = VarNode(name = "a"),
                right = ArrayNode(listOf = arrayListOf(IntNode(1), IntNode(2), IntNode(5)), type = IntType)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseArrayOfAccess() {
        val parser = makeSimpleParser("arrayOf[int](1, 2, 5)[1]")

        val node = parser.parse()

        assertEquals(
            expected = IndexNode(
                list = ArrayNode(listOf = arrayListOf(IntNode(1), IntNode(2), IntNode(5)), IntType),
                index = IntNode(1)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseArrayIndexAssign() {
        val parser = makeSimpleParser("arrayOf[int](1, 2, 5)[1] = 4")

        val node = parser.parse()

        assertEquals(
            expected = AssignNode(
                left = IndexNode(
                    list = ArrayNode(
                        listOf = arrayListOf(
                            IntNode(value = 1),
                            IntNode(value = 2),
                            IntNode(value = 5)
                        ),
                        IntType
                    ),
                    index = IntNode(value = 1)
                ),
                right = IntNode(value = 4)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseDictOf() {
        val parser = makeSimpleParser("a = dictOf[int:str]()")

        val node = parser.parse()

        assertEquals(
            expected = AssignNode(
                left = VarNode(name = "a"),
                right = DictNode(dictOf = emptyMap(), keyType = IntType, valType = StringType)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseDictOfOf() {
        val parser = makeSimpleParser("a = dictOf[int:str](1:\"a\", 2:\"b\", 5:\"c\")")

        val node = parser.parse()

        assertEquals(
            expected = AssignNode(
                left = VarNode(name = "a"),
                right = DictNode(
                    dictOf = mapOf(
                        Pair(IntNode(1), StringNode("a")),
                        Pair(IntNode(2), StringNode("b")),
                        Pair(IntNode(5), StringNode("c"))
                    ),
                    keyType = IntType,
                    valType = StringType,
                )
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseDictOfAccess() {
        val parser = makeSimpleParser("dictOf[int:bool](1:false, 2:true, 5:false)[2]")

        val node = parser.parse()

        assertEquals(
            expected = IndexNode(
                list = DictNode(
                    dictOf = mapOf(
                        Pair(IntNode(1), BoolNode(false)),
                        Pair(IntNode(2), BoolNode(true)),
                        Pair(IntNode(5), BoolNode(false))
                    ),
                    keyType = IntType,
                    valType = BoolType,
                ),
                index = IntNode(2)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseBinary() {
        val parser = makeSimpleParser("a + 4")

        val node = parser.parse()

        assertEquals(
            expected = BinaryNode(
                operator = "+",
                left = VarNode(name = "a"),
                right = IntNode(value = 4)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseDef() {
        val parser = makeSimpleParser("int a")

        val node = parser.parse()

        assertEquals(
            expected = DefNode(
                name = "a",
                type = IntType,
                def = null
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseDefAssign() {
        val parser = makeSimpleParser("int a = 5")

        val node = parser.parse()

        assertEquals(
            expected = DefNode(
                name = "a",
                type = IntType,
                def = IntNode(value = 5)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParsePropNoParams() {
        val parser = makeSimpleParser("a.str")

        val node = parser.parse()

        assertEquals(
            expected = PropNode(
                name = "str",
                args = null,
                parent = VarNode(name = "a")
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParsePropWithParams() {
        val parser = makeSimpleParser("a.shl(5)")

        val node = parser.parse()

        assertEquals(
            expected = PropNode(
                name = "shl",
                args = listOf(IntNode(value = 5)),
                parent = VarNode(name = "a")
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParsePropOnIntPrimitive() {
        val parser = makeSimpleParser("5.str")

        val node = parser.parse()

        assertEquals(
            expected = PropNode(
                name = "str",
                args = null,
                parent = IntNode(value = 5)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParsePropOnFloatPrimitive() {
        val parser = makeSimpleParser("5.0.str")

        val node = parser.parse()

        assertEquals(
            expected = PropNode(
                name = "str",
                args = null,
                parent = FloatNode(value = 5.0f)
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParsePropOnStrPrimitive() {
        val parser = makeSimpleParser("\"5\".int")

        val node = parser.parse()

        assertEquals(
            expected = PropNode(
                name = "int",
                args = null,
                parent = StringNode(value = "5")
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseClassDef() {
        val parser = makeSimpleParser("class A() {}; A a;")

        val node = parser.parse()

        assertEquals(
            expected = ProgramNode(
                prog = listOf(
                    ClassNode(
                        name = "A",
                        native = false,
                        defs = emptyList(),
                        body = VoidNode
                    ),
                    DefNode(
                        name = "a",
                        type = ClassType("A"),
                        def = null
                    )
                )
            ),
            actual = node,
        )
    }

    @Test
    fun testParseClassInstance() {
        val parser = makeSimpleParser("class A(int i) {}; new A(1);")

        val node = parser.parse()

        assertEquals(
            expected = ProgramNode(
                prog = listOf(
                    ClassNode(
                        name = "A",
                        native = false,
                        defs = listOf(
                            DefNode(
                                name = "i",
                                type = IntType,
                                def = null
                            )
                        ),
                        body = VoidNode
                    ),
                    CallNode(
                        func = VarNode(name = "A"),
                        args = listOf(
                            IntNode(value = 1)
                        )
                    )
                )
            ),
            actual = node,
        )
    }

    @Test
    fun testParseProgram() {
        val parser = makeSimpleParser("{true;\"String\"}")

        val node = parser.parse()

        assertEquals(
            expected = ProgramNode(
                prog = listOf(BoolNode(value = true), StringNode(value = "String"))
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseLet() {
        val parser = makeSimpleParser("let(int a = 5) { false }")

        val node = parser.parse()

        assertEquals(
            expected = ScopeNode(
                child = LetNode(
                    vars = listOf(
                        DefNode(
                            name = "a",
                            type = IntType,
                            def = IntNode(value = 5)
                        )
                    ),
                    body = BoolNode(value = false)
                )
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseEmptyClass() {
        val parser = makeSimpleParser("class A(int a = 5) {}")

        val node = parser.parse()

        assertEquals(
            expected = ClassNode(
                name = "A",
                defs = listOf(
                    DefNode(
                        name = "a",
                        type = IntType,
                        def = IntNode(value = 5)
                    )
                ),
                body = VoidNode,
                native = false,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseClassFields() {
        val parser = makeSimpleParser("class A(int a = 5) { int b = 6; func c(int d) bool { true } }")

        val node = parser.parse()

        assertEquals(
            expected = ClassNode(
                name = "A",
                defs = listOf(
                    DefNode(
                        name = "a",
                        type = IntType,
                        def = IntNode(value = 5)
                    )
                ),
                body = ProgramNode(
                    prog = listOf(
                        DefNode(
                            name = "b",
                            type = IntType,
                            def = IntNode(value = 6)
                        ),
                        FuncNode(
                            name = "c",
                            defs = listOf(DefNode("d", IntType, def = null)),
                            type = BoolType,
                            body = BoolNode(value = true),
                            native = false,
                        )
                    )
                ),
                native = false,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseNativeClassDeclaration() {
        val parser = makeSimpleParser("native class A() {}")

        val node = parser.parse()

        assertEquals(
            expected = ClassNode(
                name = "A",
                defs = listOf(),
                body = VoidNode,
                native = true,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseNativeClassFields() {
        val parser = makeSimpleParser("native class A() { native func n() int; }")

        val node = parser.parse()

        assertEquals(
            expected = ClassNode(
                name = "A",
                defs = listOf(),
                body = FuncNode(
                    name = "n",
                    defs = listOf(),
                    type = IntType,
                    body = VoidNode,
                    native = true,
                ),
                native = true,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseTupleType() {
        val parser = makeSimpleParser("tuple[int,str,bool] t")

        val node = parser.parse()

        assertEquals(
            expected = DefNode(
                name = "t",
                type = TupleType(
                    types = listOf(
                        IntType, StringType, BoolType
                    )
                ),
                def = null
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseTupleInit() {
        val parser = makeSimpleParser("tupleOf(1,\"a\",true)")

        val node = parser.parse()

        assertEquals(
            expected = TupleNode(
                entries = listOf(
                    IntNode(value = 1),
                    StringNode(value = "a"),
                    BoolNode(value = true),
                )
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testSingleInclude() {
        val parser = makeSimpleParser(
            str = "include \"test/abc.vel\";",
            deps = mapOf(
                "test/abc.vel" to "int a;"
            )
        )

        val node = parser.parse()

        assertEquals(
            expected = (DefNode(name = "a", type = IntType, def = null)).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testMultipleInclude() {
        val parser = makeSimpleParser(
            str = "include \"test/abc.vel\"; include \"test/xyz.vel\";",
            deps = mapOf(
                "test/abc.vel" to "int a;",
                "test/xyz.vel" to "a = 1;",
            )
        )

        val node = parser.parse()

        assertEquals(
            expected = ProgramNode(
                prog = listOf(
                    DefNode(name = "a", type = IntType, def = null),
                    AssignNode(left = VarNode(name = "a"), right = IntNode(value = 1))
                )
            ),
            actual = node,
        )
    }

    private fun Node.wrapProgram(): Node {
        return ProgramNode(listOf(this))
    }

    private fun makeSimpleParser(str: String, deps: Map<String, String> = emptyMap()): Parser {
        val input = InputStack().push(name = "test", StringInput(str))
        val stream = TokenStream(input)
        return Parser(stream, SimpleInput(deps, input))
    }

}
