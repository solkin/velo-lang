package vm.operations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import vm.Frame
import vm.LifoStack
import vm.TestUtils
import vm.VMContext
import vm.Vars
import vm.records.ArrayPtrRecord
import vm.records.BoxPtrRecord
import vm.records.NullPtrRecord
import vm.records.PtrRecord
import vm.records.ValueRecord
import vm.records.VarPtrRecord

class PointerOperationsTest {

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

    // ========== PtrNew (BoxPtrRecord) Tests ==========

    @Test
    fun `PtrNew - create pointer with integer value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        PtrNew().exec(frame, ctx)
        
        val ptr = frame.subs.pop()
        assertTrue(ptr is BoxPtrRecord)
        assertFalse(ptr.isNull())
        assertEquals(42, ptr.deref().getInt())
    }

    @Test
    fun `PtrNew - create pointer with string value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord("hello"))
        PtrNew().exec(frame, ctx)
        
        val ptr = frame.subs.pop()
        assertTrue(ptr is BoxPtrRecord)
        assertEquals("hello", ptr.deref().getString())
    }

    @Test
    fun `PtrNew - create pointer with float value`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(3.14f))
        PtrNew().exec(frame, ctx)
        
        val ptr = frame.subs.pop()
        assertTrue(ptr is BoxPtrRecord)
        assertEquals(3.14f, ptr.deref().getFloat())
    }

    // ========== PtrLoad Tests ==========

    @Test
    fun `PtrLoad - dereference box pointer`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create pointer
        frame.subs.push(ValueRecord(100))
        PtrNew().exec(frame, ctx)
        
        // Load value through pointer
        PtrLoad().exec(frame, ctx)
        
        assertEquals(100, frame.subs.pop().getInt())
    }

    @Test
    fun `PtrLoad - dereference null pointer throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(NullPtrRecord)
        
        assertFailsWith<NullPointerException> {
            PtrLoad().exec(frame, ctx)
        }
    }

    @Test
    fun `PtrLoad - non-pointer value throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(42))
        
        assertFailsWith<IllegalStateException> {
            PtrLoad().exec(frame, ctx)
        }
    }

    @Test
    fun `PtrLoad - dereference var pointer`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Set up variable
        frame.vars.vars[0] = ValueRecord(200)
        
        // Create pointer to variable
        PtrRef(0).exec(frame, ctx)
        
        // Load value through pointer
        PtrLoad().exec(frame, ctx)
        
        assertEquals(200, frame.subs.pop().getInt())
    }

    @Test
    fun `PtrLoad - dereference array pointer`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(10),
            ValueRecord(20),
            ValueRecord(30)
        )
        
        // Push array and index
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(1))
        
        // Create pointer to array element
        PtrRefIndex().exec(frame, ctx)
        
        // Load value through pointer
        PtrLoad().exec(frame, ctx)
        
        assertEquals(20, frame.subs.pop().getInt())
    }

    // ========== PtrStore Tests ==========

    @Test
    fun `PtrStore - store value through box pointer`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create pointer with initial value
        frame.subs.push(ValueRecord(100))
        PtrNew().exec(frame, ctx)
        val ptr = frame.subs.pop() as BoxPtrRecord
        
        // Store new value
        frame.subs.push(ValueRecord(200))
        frame.subs.push(ptr)
        PtrStore().exec(frame, ctx)
        
        // Verify value changed
        assertEquals(200, ptr.deref().getInt())
    }

    @Test
    fun `PtrStore - store value through null pointer throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(200))
        frame.subs.push(NullPtrRecord)
        
        assertFailsWith<NullPointerException> {
            PtrStore().exec(frame, ctx)
        }
    }

    @Test
    fun `PtrStore - store through var pointer modifies variable`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Set up variable
        frame.vars.vars[0] = ValueRecord(100)
        
        // Create pointer to variable
        PtrRef(0).exec(frame, ctx)
        val ptr = frame.subs.pop() as VarPtrRecord
        
        // Store new value through pointer
        frame.subs.push(ValueRecord(300))
        frame.subs.push(ptr)
        PtrStore().exec(frame, ctx)
        
        // Verify variable changed
        assertEquals(300, frame.vars.get(0).getInt())
        assertEquals(300, ptr.deref().getInt())
    }

    @Test
    fun `PtrStore - store through array pointer modifies array`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(10),
            ValueRecord(20),
            ValueRecord(30)
        )
        
        // Create pointer to array element
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(1))
        PtrRefIndex().exec(frame, ctx)
        val ptr = frame.subs.pop() as ArrayPtrRecord
        
        // Store new value through pointer
        frame.subs.push(ValueRecord(999))
        frame.subs.push(ptr)
        PtrStore().exec(frame, ctx)
        
        // Verify array element changed
        assertEquals(999, array[1].getInt())
        assertEquals(999, ptr.deref().getInt())
    }

    @Test
    fun `PtrStore - non-pointer value throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.subs.push(ValueRecord(200))
        frame.subs.push(ValueRecord(42)) // Not a pointer
        
        assertFailsWith<IllegalStateException> {
            PtrStore().exec(frame, ctx)
        }
    }

    // ========== PtrRef (VarPtrRecord) Tests ==========

    @Test
    fun `PtrRef - create pointer to variable`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.vars.vars[0] = ValueRecord(42)
        PtrRef(0).exec(frame, ctx)
        
        val ptr = frame.subs.pop()
        assertTrue(ptr is VarPtrRecord)
        assertFalse(ptr.isNull())
        assertEquals(42, ptr.deref().getInt())
    }

    @Test
    fun `PtrRef - pointer reflects variable changes`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.vars.vars[0] = ValueRecord(100)
        PtrRef(0).exec(frame, ctx)
        val ptr = frame.subs.pop() as VarPtrRecord
        
        // Change variable directly
        frame.vars.set(0, ValueRecord(200))
        
        // Pointer should see new value
        assertEquals(200, ptr.deref().getInt())
    }

    @Test
    fun `PtrRef - multiple pointers to same variable`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        frame.vars.vars[0] = ValueRecord(50)
        
        PtrRef(0).exec(frame, ctx)
        val ptr1 = frame.subs.pop() as VarPtrRecord
        
        PtrRef(0).exec(frame, ctx)
        val ptr2 = frame.subs.pop() as VarPtrRecord
        
        // Both pointers should point to same variable
        assertEquals(50, ptr1.deref().getInt())
        assertEquals(50, ptr2.deref().getInt())
        
        // Modify through one pointer
        ptr1.assign(ValueRecord(100))
        
        // Both should see change
        assertEquals(100, ptr1.deref().getInt())
        assertEquals(100, ptr2.deref().getInt())
        assertEquals(100, frame.vars.get(0).getInt())
    }

    @Test
    fun `PtrRef - pointer to variable in parent scope`() {
        val parentVars = Vars(
            vars = HashMap<Int, vm.Record>().apply {
                put(0, ValueRecord(500))
            },
            parent = null
        )
        
        val frame = Frame(
            pc = 0,
            subs = LifoStack(),
            vars = Vars(HashMap(), parent = parentVars),
            ops = emptyList()
        )
        
        val ctx = createTestContext()
        
        // Create pointer to variable in parent scope
        PtrRef(0).exec(frame, ctx)
        val ptr = frame.subs.pop() as VarPtrRecord
        
        assertEquals(500, ptr.deref().getInt())
        
        // Modify through pointer
        ptr.assign(ValueRecord(600))
        
        assertEquals(600, parentVars.get(0).getInt())
    }

    // ========== PtrRefIndex (ArrayPtrRecord) Tests ==========

    @Test
    fun `PtrRefIndex - create pointer to array element`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(1),
            ValueRecord(2),
            ValueRecord(3)
        )
        
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(0))
        PtrRefIndex().exec(frame, ctx)
        
        val ptr = frame.subs.pop()
        assertTrue(ptr is ArrayPtrRecord)
        assertFalse(ptr.isNull())
        assertEquals(1, ptr.deref().getInt())
    }

    @Test
    fun `PtrRefIndex - pointer to middle element`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(10),
            ValueRecord(20),
            ValueRecord(30),
            ValueRecord(40)
        )
        
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(2))
        PtrRefIndex().exec(frame, ctx)
        
        val ptr = frame.subs.pop() as ArrayPtrRecord
        assertEquals(30, ptr.deref().getInt())
    }

    @Test
    fun `PtrRefIndex - pointer reflects array changes`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(100),
            ValueRecord(200)
        )
        
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(1))
        PtrRefIndex().exec(frame, ctx)
        val ptr = frame.subs.pop() as ArrayPtrRecord
        
        // Modify array directly
        array[1] = ValueRecord(300)
        
        // Pointer should see change
        assertEquals(300, ptr.deref().getInt())
    }

    @Test
    fun `PtrRefIndex - out of bounds index throws exception on deref`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(1),
            ValueRecord(2)
        )
        
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(10)) // Out of bounds
        PtrRefIndex().exec(frame, ctx)
        
        val ptr = frame.subs.pop() as ArrayPtrRecord
        
        assertFailsWith<IndexOutOfBoundsException> {
            ptr.deref()
        }
    }

    @Test
    fun `PtrRefIndex - negative index throws exception`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(1)
        )
        
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(-1))
        PtrRefIndex().exec(frame, ctx)
        
        val ptr = frame.subs.pop() as ArrayPtrRecord
        
        assertFailsWith<IndexOutOfBoundsException> {
            ptr.deref()
        }
    }

    @Test
    fun `PtrRefIndex - out of bounds index throws exception on assign`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(1)
        )
        
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(5))
        PtrRefIndex().exec(frame, ctx)
        
        val ptr = frame.subs.pop() as ArrayPtrRecord
        
        assertFailsWith<IndexOutOfBoundsException> {
            ptr.assign(ValueRecord(999))
        }
    }

    // ========== NullPtrRecord Tests ==========

    @Test
    fun `NullPtrRecord - isNull returns true`() {
        assertTrue(NullPtrRecord.isNull())
    }

    @Test
    fun `NullPtrRecord - deref throws exception`() {
        assertFailsWith<NullPointerException> {
            NullPtrRecord.deref()
        }
    }

    @Test
    fun `NullPtrRecord - assign throws exception`() {
        assertFailsWith<NullPointerException> {
            NullPtrRecord.assign(ValueRecord(42))
        }
    }

    // ========== Integration Tests ==========

    @Test
    fun `Integration - create pointer, load, modify, load again`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create pointer
        frame.subs.push(ValueRecord(10))
        PtrNew().exec(frame, ctx)
        val ptr = frame.subs.pop() as BoxPtrRecord
        
        // Load value
        frame.subs.push(ptr)
        PtrLoad().exec(frame, ctx)
        assertEquals(10, frame.subs.pop().getInt())
        
        // Modify through pointer
        frame.subs.push(ValueRecord(20))
        frame.subs.push(ptr)
        PtrStore().exec(frame, ctx)
        
        // Load again
        frame.subs.push(ptr)
        PtrLoad().exec(frame, ctx)
        assertEquals(20, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - pointer to variable, modify through pointer`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Set up variable
        frame.vars.vars[0] = ValueRecord(100)
        
        // Create pointer
        PtrRef(0).exec(frame, ctx)
        val ptr = frame.subs.pop() as VarPtrRecord
        
        // Modify through pointer
        ptr.assign(ValueRecord(200))
        
        // Verify variable changed
        assertEquals(200, frame.vars.get(0).getInt())
        
        // Load through pointer
        frame.subs.push(ptr)
        PtrLoad().exec(frame, ctx)
        assertEquals(200, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - pointer to array element, modify through pointer`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val array = arrayOf<vm.Record>(
            ValueRecord(1),
            ValueRecord(2),
            ValueRecord(3)
        )
        
        // Create pointer to middle element
        frame.subs.push(ValueRecord(array))
        frame.subs.push(ValueRecord(1))
        PtrRefIndex().exec(frame, ctx)
        val ptr = frame.subs.pop() as ArrayPtrRecord
        
        // Modify through pointer
        ptr.assign(ValueRecord(999))
        
        // Verify array changed
        assertEquals(999, array[1].getInt())
        
        // Load through pointer
        frame.subs.push(ptr)
        PtrLoad().exec(frame, ctx)
        assertEquals(999, frame.subs.pop().getInt())
    }

    @Test
    fun `Integration - multiple pointers to same box`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create box pointer
        frame.subs.push(ValueRecord(50))
        PtrNew().exec(frame, ctx)
        val ptr1 = frame.subs.pop() as BoxPtrRecord
        
        // Create another pointer to same box (by copying the pointer)
        val ptr2 = BoxPtrRecord(ptr1.deref())
        
        // Modify through first pointer
        ptr1.assign(ValueRecord(100))
        
        // First pointer should see change
        assertEquals(100, ptr1.deref().getInt())
        
        // Second pointer points to different box, so unchanged
        assertEquals(50, ptr2.deref().getInt())
    }

    @Test
    fun `Integration - pointer chain through operations`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        // Create variable
        frame.vars.vars[0] = ValueRecord(1000)
        
        // Create pointer to variable
        PtrRef(0).exec(frame, ctx)
        val varPtr = frame.subs.pop() as VarPtrRecord
        
        // Load value and create new box pointer
        frame.subs.push(varPtr)
        PtrLoad().exec(frame, ctx)
        PtrNew().exec(frame, ctx)
        val boxPtr = frame.subs.pop() as BoxPtrRecord
        
        // Modify box pointer
        boxPtr.assign(ValueRecord(2000))
        
        // Original variable unchanged (box pointer is independent)
        assertEquals(1000, frame.vars.get(0).getInt())
        assertEquals(2000, boxPtr.deref().getInt())
    }

    @Test
    fun `Integration - null pointer check before operations`() {
        val frame = createTestFrame()
        val ctx = createTestContext()
        
        val nullPtr = NullPtrRecord
        
        // Check isNull
        assertTrue(nullPtr.isNull())
        
        // Attempting to load should throw
        frame.subs.push(nullPtr)
        assertFailsWith<NullPointerException> {
            PtrLoad().exec(frame, ctx)
        }
        
        // Attempting to store should throw
        frame.subs.push(ValueRecord(42))
        frame.subs.push(nullPtr)
        assertFailsWith<NullPointerException> {
            PtrStore().exec(frame, ctx)
        }
    }
}

