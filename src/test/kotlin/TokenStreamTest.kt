import compiler.parser.StreamInput
import compiler.parser.StringInput
import compiler.parser.Token
import compiler.parser.TokenStream
import compiler.parser.TokenType
import java.io.ByteArrayInputStream
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
            Token(
                type = TokenType.NUMBER,
                value = 123.5
            ), token
        )
    }

    @Test
    fun testNumberWithTrailingDot() {
        val input = StringInput("123.")
        val tokenStream = TokenStream(input)

        val token1 = tokenStream.next()
        val token2 = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 123
            ), token1
        )
        assertEquals(
            Token(
                type = TokenType.PUNCTUATION,
                value = '.'
            ), token2
        )
    }

    @Test
    fun testNumberWithTrailingDots() {
        val input = StringInput("123..")
        val tokenStream = TokenStream(input)

        val token1 = tokenStream.next()
        val token2 = tokenStream.next()
        val token3 = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 123
            ), token1
        )
        assertEquals(
            Token(
                type = TokenType.PUNCTUATION,
                value = '.'
            ), token2
        )
        assertEquals(
            Token(
                type = TokenType.PUNCTUATION,
                value = '.'
            ), token3
        )
    }

    @Test
    fun testNumberWithTrailingProp() {
        val input = StringInput("123.test")
        val tokenStream = TokenStream(input)

        val token1 = tokenStream.next()
        val token2 = tokenStream.next()
        val token3 = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 123
            ), token1
        )
        assertEquals(
            Token(
                type = TokenType.PUNCTUATION,
                value = '.'
            ), token2
        )
        assertEquals(
            Token(
                type = TokenType.VARIABLE,
                value = "test"
            ), token3
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