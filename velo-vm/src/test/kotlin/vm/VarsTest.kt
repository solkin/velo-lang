package vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import vm.records.EmptyRecord
import vm.records.ValueRecord

class VarsTest {

    @Test
    fun `get and set variable in same scope`() {
        val vars = createVars(listOf(0))
        vars.set(0, ValueRecord(42))
        assertEquals(42, vars.get(0).getInt())
    }

    @Test
    fun `get variable from parent scope`() {
        val parentVars = createVars(listOf(0))
        parentVars.set(0, ValueRecord(100))
        val childVars = createVars(emptyList(), parent = parentVars)
        assertEquals(100, childVars.get(0).getInt())
    }

    @Test
    fun `set variable in parent scope`() {
        val parentVars = createVars(listOf(0))
        parentVars.set(0, ValueRecord(100))
        val childVars = createVars(emptyList(), parent = parentVars)

        childVars.set(0, ValueRecord(200))
        assertEquals(200, parentVars.get(0).getInt())
        assertEquals(200, childVars.get(0).getInt())
    }

    @Test
    fun `child scope shadows parent variable`() {
        val parentVars = createVars(listOf(0))
        parentVars.set(0, ValueRecord(100))
        val childVars = createVars(listOf(0), parent = parentVars)
        childVars.set(0, ValueRecord(200))

        assertEquals(200, childVars.get(0).getInt())
        assertEquals(100, parentVars.get(0).getInt())
    }

    @Test
    fun `get undefined variable throws exception`() {
        val vars = createVars(emptyList())
        assertFailsWith<IllegalArgumentException> {
            vars.get(999)
        }
    }

    @Test
    fun `set undefined variable throws exception`() {
        val vars = createVars(emptyList())
        assertFailsWith<IllegalArgumentException> {
            vars.set(999, ValueRecord(42))
        }
    }

    @Test
    fun `empty returns true for empty vars`() {
        assertTrue(createVars(emptyList()).empty())
    }

    @Test
    fun `empty returns false for non-empty vars`() {
        assertFalse(createVars(listOf(0)).empty())
    }

    @Test
    fun `empty returns true when only parent has vars`() {
        val parentVars = createVars(listOf(0))
        parentVars.set(0, ValueRecord(100))
        val childVars = createVars(emptyList(), parent = parentVars)
        assertTrue(childVars.empty())
    }

    @Test
    fun `multiple level scope lookup`() {
        // Each scope owns one contiguous index, continuing from its parent —
        // exactly how the compiler numbers nested frames.
        val level1 = createVars(listOf(0)).apply { set(0, ValueRecord(1)) }
        val level2 = createVars(listOf(1), parent = level1).apply { set(1, ValueRecord(2)) }
        val level3 = createVars(listOf(2), parent = level2).apply { set(2, ValueRecord(3)) }

        assertEquals(1, level3.get(0).getInt())
        assertEquals(2, level3.get(1).getInt())
        assertEquals(3, level3.get(2).getInt())
    }

    @Test
    fun `createVars helper function`() {
        val vars = createVars(listOf(0, 1, 2))

        assertFalse(vars.empty())
        assertEquals(EmptyRecord, vars.get(0))
        assertEquals(EmptyRecord, vars.get(1))
        assertEquals(EmptyRecord, vars.get(2))
    }

    @Test
    fun `createVars with parent`() {
        val parent = createVars(listOf(0))
        parent.set(0, ValueRecord(100))

        val child = createVars(listOf(1, 2), parent = parent)

        assertEquals(100, child.get(0).getInt())
        assertEquals(EmptyRecord, child.get(1))
        assertEquals(EmptyRecord, child.get(2))
    }
}
