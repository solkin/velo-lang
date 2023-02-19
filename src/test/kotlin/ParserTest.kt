import nodes.*
import parser.*
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
            node, NumNode(
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
            node, StrNode(
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
    fun testParseLambda() {
        val input = StringInput("lambda(a) { false }")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parse()

        assertEquals(
            node, LambdaNode(
                name = null,
                vars = listOf("a"),
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
                args = listOf(StrNode(value = "Hello, World!"))
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
                right = NumNode(value = 4.0)
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
                right = NumNode(value = 4.0)
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
                prog = listOf(BoolNode(value = true), StrNode(value = "String"))
            ).wrapProgram()
        )
    }

    private fun Node.wrapProgram(): Node {
        return ProgramNode(listOf(this))
    }

}