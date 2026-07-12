package vm.operations

import core.Op

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import vm.Frame
import vm.LifoStack
import vm.TestUtils
import vm.VMContext
import vm.Vars
import vm.records.ValueRecord

class StackOperationsTest {

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

    // ========== Push Tests ==========

    @Test
    fun `Push - integer value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(42).exec(frame, ctx)
        
        assertEquals(42, frame.subs.pop().getInt())
    }

    @Test
    fun `Push - string value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push("hello").exec(frame, ctx)
        
        assertEquals("hello", frame.subs.pop().getString())
    }

    @Test
    fun `Push - float value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(3.14f).exec(frame, ctx)
        
        assertEquals(3.14f, frame.subs.pop().getFloat())
    }

    @Test
    fun `Push - boolean value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(true).exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `Push - multiple values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(1).exec(frame, ctx)
        Op.Push(2).exec(frame, ctx)
        Op.Push(3).exec(frame, ctx)
        
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Push - byte value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(42.toByte()).exec(frame, ctx)
        
        assertEquals(42.toByte(), frame.subs.pop().getByte())
    }

    // ========== Pop Tests ==========

    @Test
    fun `Pop - removes top element`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Op.Pop.exec(frame, ctx)
        
        // Top element (3) should be removed
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
        assertTrue(frame.subs.empty())
    }

    @Test
    fun `Pop - removes single element`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        
        Op.Pop.exec(frame, ctx)
        
        assertTrue(frame.subs.empty())
    }

    @Test
    fun `Pop - on empty stack throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        assertFailsWith<NoSuchElementException> {
            Op.Pop.exec(frame, ctx)
        }
    }

    @Test
    fun `Pop - multiple pops`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Op.Pop.exec(frame, ctx)
        Op.Pop.exec(frame, ctx)
        
        assertEquals(1, frame.subs.pop().getInt())
        assertTrue(frame.subs.empty())
    }

    // ========== Dup Tests ==========

    @Test
    fun `Dup - duplicates top element`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        
        Op.Dup.exec(frame, ctx)
        
        // Top two elements should be the same
        val top = frame.subs.pop().getInt()
        val second = frame.subs.pop().getInt()
        
        assertEquals(42, top)
        assertEquals(42, second)
    }

    @Test
    fun `Dup - duplicates on non-empty stack`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        
        Op.Dup.exec(frame, ctx)
        
        // Stack should be: [1, 2, 2] (top to bottom)
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Dup - on empty stack throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Dup uses peek() which may throw NoSuchElementException or return null
        // If null is returned, push(null) will cause NullPointerException
        assertFailsWith<Exception> {
            Op.Dup.exec(frame, ctx)
        }
    }

    @Test
    fun `Dup - multiple duplications`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(100))
        
        Op.Dup.exec(frame, ctx)
        Op.Dup.exec(frame, ctx)
        
        // Stack should be: [100, 100, 100]
        assertEquals(100, frame.subs.pop().getInt())
        assertEquals(100, frame.subs.pop().getInt())
        assertEquals(100, frame.subs.pop().getInt())
    }

    @Test
    fun `Dup - preserves value type`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord("test"))
        
        Op.Dup.exec(frame, ctx)
        
        assertEquals("test", frame.subs.pop().getString())
        assertEquals("test", frame.subs.pop().getString())
    }

    // ========== Swap Tests ==========

    @Test
    fun `Swap - swaps two top elements`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        
        Op.Swap.exec(frame, ctx)
        
        // After swap: [2, 1]
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
    }

    @Test
    fun `Swap - swaps different types`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        frame.subs.push(ValueRecord("hello"))
        
        Op.Swap.exec(frame, ctx)
        
        assertEquals(42, frame.subs.pop().getInt())
        assertEquals("hello", frame.subs.pop().getString())
    }

    @Test
    fun `Swap - on stack with one element throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        
        assertFailsWith<NoSuchElementException> {
            Op.Swap.exec(frame, ctx)
        }
    }

    @Test
    fun `Swap - on empty stack throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        assertFailsWith<NoSuchElementException> {
            Op.Swap.exec(frame, ctx)
        }
    }

    @Test
    fun `Swap - multiple swaps return to original`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        
        Op.Swap.exec(frame, ctx)
        // After first swap: [2, 1] where 1 is on top
        
        Op.Swap.exec(frame, ctx)
        // After second swap: back to [1, 2] where 2 is on top
        
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Swap - with three elements swaps only top two`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Op.Swap.exec(frame, ctx)
        
        // Stack should be: [2, 3, 1] (top to bottom)
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    // ========== Integration Tests ==========

    @Test
    fun `Integration - Push Pop sequence`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(1).exec(frame, ctx)
        Op.Push(2).exec(frame, ctx)
        Op.Pop.exec(frame, ctx)
        Op.Push(3).exec(frame, ctx)
        
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - Dup Swap combination`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Op.Push(1).exec(frame, ctx)
        Op.Dup.exec(frame, ctx)
        // Stack: [1, 1]
        
        Op.Push(2).exec(frame, ctx)
        // Stack: [2, 1, 1]
        
        Op.Swap.exec(frame, ctx)
        // Stack: [1, 2, 1]
        
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - Dup preserves reference semantics`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create a value and duplicate it
        val value = ValueRecord(42)
        frame.subs.push(value)
        Op.Dup.exec(frame, ctx)
        
        // Both should be equal but may be different instances
        val top = frame.subs.pop()
        val second = frame.subs.pop()
        
        assertEquals(42, top.getInt())
        assertEquals(42, second.getInt())
    }

}

