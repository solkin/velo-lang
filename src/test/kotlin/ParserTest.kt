import parser.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserTest {

    @Test
    fun testParseNum() {
        val input = StringInput("123.5")
        val stream = TokenStream(input)
        val parser = Parser(stream)

        val node = parser.parseAtom()

        assertEquals(
            node, NumNode(
                value = 123.5
            )
        )
    }

}