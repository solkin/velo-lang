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
import compiler.nodes.ClassNode
import compiler.nodes.ClassType
import compiler.nodes.DictNode
import compiler.nodes.PropNode
import compiler.nodes.ScopeNode
import compiler.nodes.StringNode
import compiler.nodes.StringType
import compiler.nodes.VarNode
import compiler.nodes.VoidNode
import compiler.nodes.VoidType
import compiler.parser.Parser
import compiler.parser.StringInput
import compiler.parser.TokenStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserTest {

    @Test
    fun testParseNum() {
        val input = StringInput("123.5")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            expected = FloatNode(
                value = 123.5f,
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseString() {
        val input = StringInput("\"Hello World\"")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("true")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            expected = BoolNode(
                value = true
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseVar() {
        val input = StringInput("foo")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("func(int a) bool { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("func A(int a) void { }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("ext(int i) A(int a) void { }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("println(\"Hello, World!\")")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("if (true) then false")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("if (true) then false else true")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("if (true) { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("if (true) { false } else { true }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a = 4")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a = arrayOf[int]()")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a = arrayOf[int](1, 2, 5)")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("arrayOf[int](1, 2, 5)[1]")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
    fun testParseDictOf() {
        val input = StringInput("a = dictOf[int:str]()")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a = dictOf[int:str](1:\"a\", 2:\"b\", 5:\"c\")")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("dictOf[int:bool](1:false, 2:true, 5:false)[2]")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a + 4")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("int a")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("int a = 5")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a.str")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("a.shl(5)")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("5.str")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("5.0.str")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("\"5\".int")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("class[A] a")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            expected = DefNode(
                name = "a",
                type = ClassType("A"),
                def = null
            ).wrapProgram(),
            actual = node,
        )
    }

    @Test
    fun testParseProgram() {
        val input = StringInput("{true;\"String\"}")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("let(int a = 5) { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("class A(int a = 5) {}")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("class A(int a = 5) { int b = 6; func c(int d) bool { true } }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("native class A() {}")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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
        val input = StringInput("native class A() { native func n() int; }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

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

    private fun Node.wrapProgram(): Node {
        return ProgramNode(listOf(this))
    }

}
