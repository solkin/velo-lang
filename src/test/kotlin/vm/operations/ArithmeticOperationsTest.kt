package vm.operations

import kotlin.test.Test
import kotlin.test.assertEquals
import vm.Frame
import vm.LifoStack
import vm.TestUtils
import vm.VMContext
import vm.records.ValueRecord

class ArithmeticOperationsTest {

    private fun createTestFrame(): Frame {
        return Frame(
            pc = 0,
            subs = LifoStack(),
            vars = vm.Vars(HashMap(), null),
            ops = emptyList()
        )
    }

    private fun createTestContext(): VMContext {
        return TestUtils.createTestContext()
    }

    @Test
    fun `Add - two integers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(3))
        frame.subs.push(ValueRecord(5))
        
        Add().exec(frame, ctx)
        
        assertEquals(8, frame.subs.pop().getInt())
    }

    @Test
    fun `Add - integer and float promotes to float`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(3.5f))
        frame.subs.push(ValueRecord(5))
        
        Add().exec(frame, ctx)
        
        assertEquals(8.5f, frame.subs.pop().getFloat())
    }

    @Test
    fun `Add - two floats`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(2.5f))
        frame.subs.push(ValueRecord(3.7f))
        
        Add().exec(frame, ctx)
        
        assertEquals(6.2f, frame.subs.pop().getFloat())
    }

    @Test
    fun `Add - byte and int`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(10.toByte()))
        frame.subs.push(ValueRecord(20))
        
        Add().exec(frame, ctx)
        
        assertEquals(30, frame.subs.pop().getInt())
    }

    @Test
    fun `Sub - two integers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(10), push(3) -> [10, 3] where 3 is on top
        // Pop: rec1=3, rec2=10, result = 10 - 3 = 7
        frame.subs.push(ValueRecord(10))
        frame.subs.push(ValueRecord(3))
        
        Sub().exec(frame, ctx)
        
        assertEquals(7, frame.subs.pop().getInt())
    }

    @Test
    fun `Sub - negative result`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(3), push(10) -> [3, 10] where 10 is on top
        // Pop: rec1=10, rec2=3, result = 3 - 10 = -7
        frame.subs.push(ValueRecord(3))
        frame.subs.push(ValueRecord(10))
        
        Sub().exec(frame, ctx)
        
        assertEquals(-7, frame.subs.pop().getInt())
    }

    @Test
    fun `Sub - float and int`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(10.0f), push(2.5f) -> [10.0f, 2.5f] where 2.5f is on top
        // Pop: rec1=2.5f, rec2=10.0f, result = 10.0f - 2.5f = 7.5f
        // Note: When mixing Int and Float, the result type depends on the first operand type
        // So we use float for both to get float result
        frame.subs.push(ValueRecord(10.0f))
        frame.subs.push(ValueRecord(2.5f))
        
        Sub().exec(frame, ctx)
        
        assertEquals(7.5f, frame.subs.pop().getFloat())
    }

    @Test
    fun `Mul - two integers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(3))
        frame.subs.push(ValueRecord(4))
        
        Mul().exec(frame, ctx)
        
        assertEquals(12, frame.subs.pop().getInt())
    }

    @Test
    fun `Mul - by zero`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0))
        frame.subs.push(ValueRecord(42))
        
        Mul().exec(frame, ctx)
        
        assertEquals(0, frame.subs.pop().getInt())
    }

    @Test
    fun `Mul - float and int`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(2.5f))
        frame.subs.push(ValueRecord(4))
        
        Mul().exec(frame, ctx)
        
        assertEquals(10.0f, frame.subs.pop().getFloat())
    }

    @Test
    fun `Div - two integers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(10), push(3) -> [10, 3] where 3 is on top
        // Pop: rec1=3, rec2=10, result = 10 / 3 = 3 (integer division)
        frame.subs.push(ValueRecord(10))
        frame.subs.push(ValueRecord(3))
        
        Div().exec(frame, ctx)
        
        assertEquals(3, frame.subs.pop().getInt()) // Integer division
    }

    @Test
    fun `Div - float division`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(10.0f), push(3.0f) -> [10.0f, 3.0f] where 3.0f is on top
        // Pop: rec1=3.0f, rec2=10.0f, result = 10.0f / 3.0f = 3.333...
        frame.subs.push(ValueRecord(10.0f))
        frame.subs.push(ValueRecord(3.0f))
        
        Div().exec(frame, ctx)
        
        val result = frame.subs.pop().getFloat()
        assertEquals(3.3333333f, result, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `Div - by one`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(42), push(1) -> [42, 1] where 1 is on top
        // Pop: rec1=1, rec2=42, result = 42 / 1 = 42
        frame.subs.push(ValueRecord(42))
        frame.subs.push(ValueRecord(1))
        
        Div().exec(frame, ctx)
        
        assertEquals(42, frame.subs.pop().getInt())
    }

    @Test
    fun `Rem - modulo operation`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(10), push(3) -> [10, 3] where 3 is on top
        // Pop: rec1=3, rec2=10, result = 10 % 3 = 1
        frame.subs.push(ValueRecord(10))
        frame.subs.push(ValueRecord(3))
        
        Rem().exec(frame, ctx)
        
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Rem - exact division`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(10), push(5) -> [10, 5] where 5 is on top
        // Pop: rec1=5, rec2=10, result = 10 % 5 = 0
        frame.subs.push(ValueRecord(10))
        frame.subs.push(ValueRecord(5))
        
        Rem().exec(frame, ctx)
        
        assertEquals(0, frame.subs.pop().getInt())
    }

    @Test
    fun `Rem - larger divisor`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(3), push(10) -> [3, 10] where 10 is on top
        // Pop: rec1=10, rec2=3, result = 3 % 10 = 3
        frame.subs.push(ValueRecord(3))
        frame.subs.push(ValueRecord(10))
        
        Rem().exec(frame, ctx)
        
        assertEquals(3, frame.subs.pop().getInt())
    }

    @Test
    fun `Add - order matters (stack order)`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(5), push(3) -> [5, 3] where 3 is on top
        // Add: rec1=3, rec2=5, result = 5 + 3 = 8
        frame.subs.push(ValueRecord(5))
        frame.subs.push(ValueRecord(3))
        
        Add().exec(frame, ctx)
        
        assertEquals(8, frame.subs.pop().getInt())
    }

    @Test
    fun `Mul - large numbers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1000))
        frame.subs.push(ValueRecord(2000))
        
        Mul().exec(frame, ctx)
        
        assertEquals(2000000, frame.subs.pop().getInt())
    }
}

