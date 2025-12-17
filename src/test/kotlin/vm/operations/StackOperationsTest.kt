package vm.operations

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
            vars = Vars(HashMap(), null),
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
        
        Push(42).exec(frame, ctx)
        
        assertEquals(42, frame.subs.pop().getInt())
    }

    @Test
    fun `Push - string value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push("hello").exec(frame, ctx)
        
        assertEquals("hello", frame.subs.pop().getString())
    }

    @Test
    fun `Push - float value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(3.14f).exec(frame, ctx)
        
        assertEquals(3.14f, frame.subs.pop().getFloat())
    }

    @Test
    fun `Push - boolean value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(true).exec(frame, ctx)
        
        assertTrue(frame.subs.pop().getBool())
    }

    @Test
    fun `Push - multiple values`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(1).exec(frame, ctx)
        Push(2).exec(frame, ctx)
        Push(3).exec(frame, ctx)
        
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Push - byte value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(42.toByte()).exec(frame, ctx)
        
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
        
        Pop().exec(frame, ctx)
        
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
        
        Pop().exec(frame, ctx)
        
        assertTrue(frame.subs.empty())
    }

    @Test
    fun `Pop - on empty stack throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        assertFailsWith<NoSuchElementException> {
            Pop().exec(frame, ctx)
        }
    }

    @Test
    fun `Pop - multiple pops`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Pop().exec(frame, ctx)
        Pop().exec(frame, ctx)
        
        assertEquals(1, frame.subs.pop().getInt())
        assertTrue(frame.subs.empty())
    }

    // ========== Dup Tests ==========

    @Test
    fun `Dup - duplicates top element`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        
        Dup().exec(frame, ctx)
        
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
        
        Dup().exec(frame, ctx)
        
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
            Dup().exec(frame, ctx)
        }
    }

    @Test
    fun `Dup - multiple duplications`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(100))
        
        Dup().exec(frame, ctx)
        Dup().exec(frame, ctx)
        
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
        
        Dup().exec(frame, ctx)
        
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
        
        Swap().exec(frame, ctx)
        
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
        
        Swap().exec(frame, ctx)
        
        assertEquals(42, frame.subs.pop().getInt())
        assertEquals("hello", frame.subs.pop().getString())
    }

    @Test
    fun `Swap - on stack with one element throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        
        assertFailsWith<NoSuchElementException> {
            Swap().exec(frame, ctx)
        }
    }

    @Test
    fun `Swap - on empty stack throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        assertFailsWith<NoSuchElementException> {
            Swap().exec(frame, ctx)
        }
    }

    @Test
    fun `Swap - multiple swaps return to original`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        
        Swap().exec(frame, ctx)
        // After first swap: [2, 1] where 1 is on top
        
        Swap().exec(frame, ctx)
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
        
        Swap().exec(frame, ctx)
        
        // Stack should be: [2, 3, 1] (top to bottom)
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    // ========== Rot Tests ==========

    @Test
    fun `Rot - rotates three top elements`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: [1, 2, 3] (bottom to top, where 3 is on top)
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Rot().exec(frame, ctx)
        
        // Rot: pop 3 (rec1), pop 2 (rec2), pop 1 (rec3), then push rec2, push rec1, push rec3
        // push(2), push(3), push(1) -> [2, 3, 1] where 1 is on top
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
    }

    @Test
    fun `Rot - with different types`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Stack: [1, "two", 3.0f] (bottom to top, where 3.0f is on top)
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord("two"))
        frame.subs.push(ValueRecord(3.0f))
        
        Rot().exec(frame, ctx)
        
        // Rot: pop 3.0f (rec1), pop "two" (rec2), pop 1 (rec3), then push rec2, push rec1, push rec3
        // push("two"), push(3.0f), push(1) -> ["two", 3.0f, 1] where 1 is on top
        val top = frame.subs.pop()
        val second = frame.subs.pop()
        val third = frame.subs.pop()
        
        // Verify values using get<Any>() to avoid type casting issues
        assertEquals(1, top.get<Any>() as Int)
        assertEquals(3.0f, second.get<Any>() as Float)
        assertEquals("two", third.get<Any>() as String)
    }

    @Test
    fun `Rot - on stack with two elements throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        
        assertFailsWith<NoSuchElementException> {
            Rot().exec(frame, ctx)
        }
    }

    @Test
    fun `Rot - on stack with one element throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        
        assertFailsWith<NoSuchElementException> {
            Rot().exec(frame, ctx)
        }
    }

    @Test
    fun `Rot - on empty stack throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        assertFailsWith<NoSuchElementException> {
            Rot().exec(frame, ctx)
        }
    }

    @Test
    fun `Rot - multiple rotations`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Rot().exec(frame, ctx)
        // After first rot: [2, 3, 1] where 1 is on top
        
        Rot().exec(frame, ctx)
        // After second rot on [2, 3, 1]:
        // pop 1 (rec1), pop 3 (rec2), pop 2 (rec3)
        // push(3), push(1), push(2) -> [3, 1, 2] where 2 is on top
        
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
    }

    @Test
    fun `Rot - with more than three elements`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(0))
        frame.subs.push(ValueRecord(1))
        frame.subs.push(ValueRecord(2))
        frame.subs.push(ValueRecord(3))
        
        Rot().exec(frame, ctx)
        
        // Only top three rotated: [0, 2, 3, 1] where 1 is on top
        // pop 3 (rec1), pop 2 (rec2), pop 1 (rec3)
        // push(2), push(3), push(1)
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(0, frame.subs.pop().getInt())
    }

    // ========== Integration Tests ==========

    @Test
    fun `Integration - Push Pop sequence`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(1).exec(frame, ctx)
        Push(2).exec(frame, ctx)
        Pop().exec(frame, ctx)
        Push(3).exec(frame, ctx)
        
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - Dup Swap combination`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(1).exec(frame, ctx)
        Dup().exec(frame, ctx)
        // Stack: [1, 1]
        
        Push(2).exec(frame, ctx)
        // Stack: [2, 1, 1]
        
        Swap().exec(frame, ctx)
        // Stack: [1, 2, 1]
        
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - Rot Swap combination`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        Push(1).exec(frame, ctx)
        Push(2).exec(frame, ctx)
        Push(3).exec(frame, ctx)
        
        Rot().exec(frame, ctx)
        // Stack: [2, 3, 1] where 1 is on top
        
        Swap().exec(frame, ctx)
        // Swap: pop 1, pop 3 -> push 1, push 3 -> [2, 1, 3] where 3 is on top
        
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - complex stack manipulation`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Build stack: [1, 2, 3] where 3 is on top
        Push(1).exec(frame, ctx)
        Push(2).exec(frame, ctx)
        Push(3).exec(frame, ctx)
        
        // Rot: [2, 3, 1] where 1 is on top
        Rot().exec(frame, ctx)
        
        // Dup top: [2, 3, 1, 1] where 1 is on top
        Dup().exec(frame, ctx)
        
        // Swap top two: [2, 3, 1, 1] (no change because both are 1)
        Swap().exec(frame, ctx)
        
        // Pop: [2, 3, 1] where 1 is on top
        Pop().exec(frame, ctx)
        
        assertEquals(1, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - Dup preserves reference semantics`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create a value and duplicate it
        val value = ValueRecord(42)
        frame.subs.push(value)
        Dup().exec(frame, ctx)
        
        // Both should be equal but may be different instances
        val top = frame.subs.pop()
        val second = frame.subs.pop()
        
        assertEquals(42, top.getInt())
        assertEquals(42, second.getInt())
    }

    @Test
    fun `Integration - all operations preserve stack order`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Initial: [1, 2, 3, 4] where 4 is on top
        Push(1).exec(frame, ctx)
        Push(2).exec(frame, ctx)
        Push(3).exec(frame, ctx)
        Push(4).exec(frame, ctx)
        
        // Rot top 3: pop 4, pop 3, pop 2 -> push 3, push 4, push 2
        // Result: [1, 3, 4, 2] where 2 is on top
        Rot().exec(frame, ctx)
        
        // Swap top 2: pop 2, pop 4 -> push 2, push 4
        // Result: [1, 3, 2, 4] where 4 is on top
        Swap().exec(frame, ctx)
        
        // Dup top: [1, 3, 2, 4, 4] where 4 is on top
        Dup().exec(frame, ctx)
        
        // Pop: [1, 3, 2, 4] where 4 is on top
        Pop().exec(frame, ctx)
        
        assertEquals(4, frame.subs.pop().getInt())
        assertEquals(2, frame.subs.pop().getInt())
        assertEquals(3, frame.subs.pop().getInt())
        assertEquals(1, frame.subs.pop().getInt())
    }
}

