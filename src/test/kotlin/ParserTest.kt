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
import compiler.nodes.StringNode
import compiler.nodes.VarNode
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
            node, FloatNode(
                value = 123.5
            ).wrapProgram()
        )
    }

    @Test
    fun testParseString() {
        val input = StringInput("\"Hello World\"")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, StringNode(
                value = "Hello World"
            ).wrapProgram()
        )
    }

    @Test
    fun testParseBoolean() {
        val input = StringInput("true")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, BoolNode(
                value = true
            ).wrapProgram()
        )
    }

    @Test
    fun testParseVar() {
        val input = StringInput("foo")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, VarNode(
                name = "foo"
            ).wrapProgram()
        )
    }

    @Test
    fun testParseFunc() {
        val input = StringInput("func(int a) bool { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, FuncNode(
                name = null,
                defs = listOf(DefNode("a", IntType, def = null)),
                type = BoolType,
                body = BoolNode(value = false)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseCall() {
        val input = StringInput("println(\"Hello, World!\")")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, CallNode(
                func = VarNode(name = "println"),
                args = listOf(StringNode(value = "Hello, World!"))
            ).wrapProgram()
        )
    }

    @Test
    fun testParseIfThen() {
        val input = StringInput("if (true) then false")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = null
            ).wrapProgram()
        )
    }

    @Test
    fun testParseIfThenElse() {
        val input = StringInput("if (true) then false else true")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = BoolNode(value = true)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseIf() {
        val input = StringInput("if (true) { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = null
            ).wrapProgram()
        )
    }

    @Test
    fun testParseElse() {
        val input = StringInput("if (true) { false } else { true }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, IfNode(
                condNode = BoolNode(value = true),
                thenNode = BoolNode(value = false),
                elseNode = BoolNode(value = true)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseAssign() {
        val input = StringInput("a = 4")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, AssignNode(
                left = VarNode(name = "a"),
                right = IntNode(value = 4)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseArrayOf() {
        val input = StringInput("a = arrayOf[int]()")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, AssignNode(
                left = VarNode(name = "a"),
                right = ArrayNode(listOf = emptyList(), IntType)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseArrayOfOf() {
        val input = StringInput("a = arrayOf[int](1, 2, 5)")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, AssignNode(
                left = VarNode(name = "a"),
                right = ArrayNode(listOf = arrayListOf(IntNode(1), IntNode(2), IntNode(5)), type = IntType)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseArrayOfAccess() {
        val input = StringInput("arrayOf[int](1, 2, 5)[1]")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, IndexNode(
                list = ArrayNode(listOf = arrayListOf(IntNode(1), IntNode(2), IntNode(5)), IntType),
                index = IntNode(1)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseBinary() {
        val input = StringInput("a + 4")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, BinaryNode(
                operator = "+",
                left = VarNode(name = "a"),
                right = IntNode(value = 4)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseDef() {
        val input = StringInput("int a")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, DefNode(
                name = "a",
                type = IntType,
                def = null
            ).wrapProgram()
        )
    }

    @Test
    fun testParseDefAssign() {
        val input = StringInput("int a = 5")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, DefNode(
                name = "a",
                type = IntType,
                def = IntNode(value = 5)
            ).wrapProgram()
        )
    }

    @Test
    fun testParseProgram() {
        val input = StringInput("{true;\"String\"}")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, ProgramNode(
                prog = listOf(BoolNode(value = true), StringNode(value = "String"))
            ).wrapProgram()
        )
    }

    @Test
    fun testParseLet() {
        val input = StringInput("let(int a = 5) { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, LetNode(
                vars = listOf(
                    DefNode(
                        name = "a",
                        type = IntType,
                        def = IntNode(value = 5)
                    )
                ),
                body = BoolNode(value = false)
            ).wrapProgram()
        )
    }

    private fun Node.wrapProgram(): Node {
        return ProgramNode(listOf(this))
    }

}