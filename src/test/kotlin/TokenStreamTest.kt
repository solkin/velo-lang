import compiler.parser.StringInput
import compiler.parser.Token
import compiler.parser.TokenStream
import compiler.parser.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
                value = 123.5f
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
    fun testNumDelimiter() {
        val input = StringInput("10_7374_1824")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 1073741824
            ), token
        )
    }

    @Test
    fun testHexNumber() {
        val input = StringInput("0xCafe")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 51966
            ), token
        )
    }

    @Test
    fun testHexNumberInvalidPrefix() {
        val input = StringInput("0xxcafe")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 0
            ), token
        )
    }

    @Test
    fun testHexNumberInvalidFormat() {
        val input = StringInput("0x0axe")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 10
            ), token
        )
    }

    @Test
    fun testHexNumberInvalidSymbols() {
        val input = StringInput("0x0n7")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 0
            ), token
        )
    }

    @Test
    fun testHexDelimiter() {
        val input = StringInput("0xca_fe")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 51966
            ), token
        )
    }

    @Test
    fun testBinNumber() {
        val input = StringInput("0b101010")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 42
            ), token
        )
    }

    @Test
    fun testBinNumberInvalidPrefixMid() {
        val input = StringInput("0bb101010")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 0
            ), token
        )
    }

    @Test
    fun testBinNumberInvalidPrefixStart() {
        val input = StringInput("1b101010")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 1
            ), token
        )
    }

    @Test
    fun testBinNumberInvalidFormat() {
        val input = StringInput("0b10b010")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 2
            ), token
        )
    }

    @Test
    fun testBinNumberInvalidSymbols() {
        val input = StringInput("0b102010")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 2
            ), token
        )
    }

    @Test
    fun testBinDelimiter() {
        val input = StringInput("0b10_10_10")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 42
            ), token
        )
    }

    @Test
    fun testBytePostfix() {
        val input = StringInput("123y")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 123.toByte()
            ), token
        )
    }

    @Test
    fun testFloatPostfix() {
        val input = StringInput("123f")
        val tokenStream = TokenStream(input)

        val token = tokenStream.next()

        assertEquals(
            Token(
                type = TokenType.NUMBER,
                value = 123f
            ), token
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