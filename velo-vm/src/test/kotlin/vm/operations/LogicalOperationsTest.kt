package vm.operations

import core.Op

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import vm.Frame
import vm.LifoStack
import vm.TestUtils
import vm.VMContext
import vm.records.ValueRecord

class LogicalOperationsTest {

    private fun createTestFrame(): Frame {
        return Frame(
            pc = 0,
            subs = LifoStack(),
            vars = vm.createVars(emptyList(), null),
            ops = emptyList()
        )
    }

    private fun createTestContext(): VMContext {
        return TestUtils.createTestContext()
    }

    @Test
    fun `And - bitwise AND operation`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0b1010)) // 10
        frame.subs.push(ValueRecord(0b1100)) // 12
        
        Op.And.exec(frame, ctx)
        
        assertEquals(0b1000, frame.subs.pop().getInt()) // 8
    }

    @Test
    fun `And - all bits set`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0b1111))
        frame.subs.push(ValueRecord(0b1111))
        
        Op.And.exec(frame, ctx)
        
        assertEquals(0b1111, frame.subs.pop().getInt())
    }

    @Test
    fun `And - with zero`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0))
        frame.subs.push(ValueRecord(0b1111))
        
        Op.And.exec(frame, ctx)
        
        assertEquals(0, frame.subs.pop().getInt())
    }

    @Test
    fun `Or - bitwise OR operation`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0b1010)) // 10
        frame.subs.push(ValueRecord(0b1100)) // 12
        
        Op.Or.exec(frame, ctx)
        
        assertEquals(0b1110, frame.subs.pop().getInt()) // 14
    }

    @Test
    fun `Or - with zero`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0))
        frame.subs.push(ValueRecord(0b1010))
        
        Op.Or.exec(frame, ctx)
        
        assertEquals(0b1010, frame.subs.pop().getInt())
    }

    @Test
    fun `Or - all bits set`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0b1111))
        frame.subs.push(ValueRecord(0b1111))
        
        Op.Or.exec(frame, ctx)
        
        assertEquals(0b1111, frame.subs.pop().getInt())
    }

    @Test
    fun `Xor - bitwise XOR operation`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0b1010)) // 10
        frame.subs.push(ValueRecord(0b1100)) // 12
        
        Op.Xor.exec(frame, ctx)
        
        assertEquals(0b0110, frame.subs.pop().getInt()) // 6
    }

    @Test
    fun `Xor - same values cancel out`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0b1010))
        frame.subs.push(ValueRecord(0b1010))
        
        Op.Xor.exec(frame, ctx)
        
        assertEquals(0, frame.subs.pop().getInt())
    }

    @Test
    fun `Xor - with zero`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0))
        frame.subs.push(ValueRecord(0b1010))
        
        Op.Xor.exec(frame, ctx)
        
        assertEquals(0b1010, frame.subs.pop().getInt())
    }

    @Test
    fun `Equals - same integer values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        frame.subs.push(ValueRecord(42))
        
        Op.Equals.exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `Equals - different integer values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        frame.subs.push(ValueRecord(43))
        
        Op.Equals.exec(frame, ctx)
        
        assertFalse(frame.subs.pop().getBool())
    }

    @Test
    fun `Equals - same string values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord("hello"))
        frame.subs.push(ValueRecord("hello"))
        
        Op.Equals.exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `Equals - different string values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord("hello"))
        frame.subs.push(ValueRecord("world"))
        
        Op.Equals.exec(frame, ctx)
        
        assertFalse(frame.subs.pop().getBool())
    }

    @Test
    fun `Equals - same float values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(3.14f))
        frame.subs.push(ValueRecord(3.14f))
        
        Op.Equals.exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `Equals - different types`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        frame.subs.push(ValueRecord("42"))
        
        Op.Equals.exec(frame, ctx)
        
        assertFalse(frame.subs.pop().getBool())
    }

    @Test
    fun `More - greater than true`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(5), push(3) -> [5, 3] where 3 is on top
        // More: val1=3, val2=5, result = 5 > 3 = true
        frame.subs.push(ValueRecord(5))
        frame.subs.push(ValueRecord(3))
        
        Op.More.exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `More - greater than false`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(3), push(5) -> [3, 5] where 5 is on top
        // More: val1=5, val2=3, result = 3 > 5 = false
        frame.subs.push(ValueRecord(3))
        frame.subs.push(ValueRecord(5))
        
        Op.More.exec(frame, ctx)
        
        assertFalse(frame.subs.pop().getBool())
    }

    @Test
    fun `More - equal values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(5), push(5) -> [5, 5] where 5 is on top
        // More: val1=5, val2=5, result = 5 > 5 = false
        frame.subs.push(ValueRecord(5))
        frame.subs.push(ValueRecord(5))
        
        Op.More.exec(frame, ctx)
        
        assertFalse(frame.subs.pop().getBool())
    }

    @Test
    fun `More - negative numbers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(-5), push(-10) -> [-5, -10] where -10 is on top
        // More: val1=-10, val2=-5, result = -5 > -10 = true
        frame.subs.push(ValueRecord(-5))
        frame.subs.push(ValueRecord(-10))
        
        Op.More.exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `More - order matters (stack order)`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: push(5), push(3) -> [5, 3] where 3 is on top
        // More: val1=3, val2=5, result = 5 > 3 = true
        frame.subs.push(ValueRecord(5))
        frame.subs.push(ValueRecord(3))
        
        Op.More.exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `And - large numbers`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0xFFFF))
        frame.subs.push(ValueRecord(0xFF00))
        
        Op.And.exec(frame, ctx)
        
        assertEquals(0xFF00, frame.subs.pop().getInt())
    }
}

