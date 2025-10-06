import compiler.parser.Input
import compiler.parser.InputStack
import compiler.parser.StringInput
import java.lang.Character.MIN_VALUE
import kotlin.test.Test
import kotlin.test.assertEquals

class InputStackTest {

    @Test
    fun testPeek() {
        val input: Input = InputStack(start = StringInput("12"))
        val ch1 = input.peek()
        val ch2 = input.peek()
        assertEquals(ch1, '1')
        assertEquals(ch2, '1')
    }

    @Test
    fun testRead() {
        val input: Input = InputStack(start = StringInput("12"))
        val ch1 = input.next()
        val ch2 = input.next()
        val ch3 = input.next()
        assertEquals(ch1, '1')
        assertEquals(ch2, '2')
        assertEquals(ch3, MIN_VALUE)
    }

    @Test
    fun testReadPeek() {
        val input: Input = InputStack(start = StringInput("12"))
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
    fun testMarkReset() {
        val input: Input = InputStack(start = StringInput("123"))
        val ch1 = input.next()
        input.mark()
        val ch2 = input.next()
        val ch3 = input.next()
        input.reset()
        val ch4 = input.next()
        assertEquals('1', ch1)
        assertEquals('2', ch2)
        assertEquals('3', ch3)
        assertEquals('2', ch4)
    }

    @Test
    fun testMarkResetPeek() {
        val input: Input = InputStack(start = StringInput("123"))
        val ch1 = input.next()
        input.mark()
        val ch2 = input.next()
        val ch3 = input.next()
        input.reset()
        val ch4 = input.peek()
        assertEquals('1', ch1)
        assertEquals('2', ch2)
        assertEquals('3', ch3)
        assertEquals('2', ch4)
    }

    @Test
    fun testEof() {
        val input: Input = InputStack(start = StringInput("12"))
        val ch1 = input.next()
        val eof1 = input.eof()
        val ch2 = input.next()
        val eof2 = input.eof()
        assertEquals(ch1, '1')
        assertEquals(ch2, '2')
        assertEquals(eof1, false)
        assertEquals(eof2, true)
    }

    @Test
    fun testPush() {
        val input = InputStack(start = StringInput("1"))
        input.push(input = StringInput("2"))
        val ch1 = input.next()
        val eof1 = input.eof()
        val ch2 = input.next()
        val eof2 = input.eof()
        assertEquals(ch1, '2')
        assertEquals(ch2, '1')
        assertEquals(eof1, false)
        assertEquals(eof2, true)
    }

    @Test
    fun testPushEof() {
        val input = InputStack(start = StringInput("1"))
        val ch1 = input.next()
        input.push(input = StringInput("2"))
        val eof1 = input.eof()
        val ch2 = input.next()
        val eof2 = input.eof()
        assertEquals(ch1, '1')
        assertEquals(ch2, '2')
        assertEquals(eof1, false)
        assertEquals(eof2, true)
    }
}