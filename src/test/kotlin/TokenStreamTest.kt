import parser.StringInput
import parser.Token
import parser.TokenStream
import parser.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStreamTest {

    @Test
    fun testNumber() {
        val input = StringInput("123.5")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            token, Token(
                type = TokenType.NUMBER,
                value = 123.5
            )
        )
    }

    @Test
    fun testString() {
        val input = StringInput("\"Hello World\"")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            token, Token(
                type = TokenType.STRING,
                value = "Hello World"
            )
        )
    }

    @Test
    fun testVar() {
        val input = StringInput("foo")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            token, Token(
                type = TokenType.VARIABLE,
                value = "foo"
            )
        )
    }

    @Test
    fun testKeyword() {
        val input = StringInput("if")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            token, Token(
                type = TokenType.KEYWORD,
                value = "if"
            )
        )
    }

    @Test
    fun testPunctuation() {
        val input = StringInput("{")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            token, Token(
                type = TokenType.PUNCTUATION,
                value = '{'
            )
        )
    }

    @Test
    fun testOperator() {
        val input = StringInput("*")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            token, Token(
                type = TokenType.OPERATOR,
                value = "*"
            )
        )
    }

    @Test
    fun testComment() {
        val input = StringInput("#first comment\n#second comment")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertNull(token)
    }
}