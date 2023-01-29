import parser.Input
import parser.StringInput
import java.lang.Character.MIN_VALUE
import kotlin.test.Test
import kotlin.test.assertEquals

class StringInputTest {

    @Test
    fun testPeek() {
        val input: Input = StringInput("12")
        val ch1 = input.peek()
        val ch2 = input.peek()
        assertEquals(ch1, '1')
        assertEquals(ch2, '1')
    }

    @Test
    fun testRead() {
        val input: Input = StringInput("12")
        val ch1 = input.next()
        val ch2 = input.next()
        val ch3 = input.next()
        assertEquals(ch1, '1')
        assertEquals(ch2, '2')
        assertEquals(ch3, MIN_VALUE)
    }

    @Test
    fun testReadPeek() {
        val input: Input = StringInput("12")
        val ch1 = input.next()
        val ch2 = input.peek()
        val ch3 = input.peek()
        val ch4 = input.next()
        assertEquals(ch1, '1')
        assertEquals(ch2, '2')
        assertEquals(ch3, '2')
        assertEquals(ch4, '2')
    }

    @Test
    fun testEof() {
        val input: Input = StringInput("12")
        val ch1 = input.next()
        val eof1 = input.eof()
        val ch2 = input.next()
        val eof2 = input.eof()
        assertEquals(ch1, '1')
        assertEquals(ch2, '2')
        assertEquals(eof1, false)
        assertEquals(eof2, true)
    }
}