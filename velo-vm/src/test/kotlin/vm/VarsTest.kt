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
        val vars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, EmptyRecord)
            },
            parent = null
        )
        
        vars.set(0, ValueRecord(42))
        assertEquals(42, vars.get(0).getInt())
    }

    @Test
    fun `get variable from parent scope`() {
        val parentVars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(100))
            },
            parent = null
        )
        
        val childVars = Vars(
            vars = HashMap(),
            parent = parentVars
        )
        
        assertEquals(100, childVars.get(0).getInt())
    }

    @Test
    fun `set variable in parent scope`() {
        val parentVars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(100))
            },
            parent = null
        )
        
        val childVars = Vars(
            vars = HashMap(),
            parent = parentVars
        )
        
        childVars.set(0, ValueRecord(200))
        assertEquals(200, parentVars.get(0).getInt())
        assertEquals(200, childVars.get(0).getInt())
    }

    @Test
    fun `child scope shadows parent variable`() {
        val parentVars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(100))
            },
            parent = null
        )
        
        val childVars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(200))
            },
            parent = parentVars
        )
        
        assertEquals(200, childVars.get(0).getInt())
        assertEquals(100, parentVars.get(0).getInt())
    }

    @Test
    fun `get undefined variable throws exception`() {
        val vars = Vars(
            vars = HashMap(),
            parent = null
        )
        
        assertFailsWith<IllegalArgumentException> {
            vars.get(999)
        }
    }

    @Test
    fun `set undefined variable throws exception`() {
        val vars = Vars(
            vars = HashMap(),
            parent = null
        )
        
        assertFailsWith<IllegalArgumentException> {
            vars.set(999, ValueRecord(42))
        }
    }

    @Test
    fun `empty returns true for empty vars`() {
        val vars = Vars(
            vars = HashMap(),
            parent = null
        )
        assertTrue(vars.empty())
    }

    @Test
    fun `empty returns false for non-empty vars`() {
        val vars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(42))
            },
            parent = null
        )
        assertFalse(vars.empty())
    }

    @Test
    fun `empty returns true when only parent has vars`() {
        val parentVars = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(100))
            },
            parent = null
        )
        
        val childVars = Vars(
            vars = HashMap(),
            parent = parentVars
        )
        
        assertTrue(childVars.empty())
    }

    @Test
    fun `multiple level scope lookup`() {
        val level1 = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(1))
            },
            parent = null
        )
        
        val level2 = Vars(
            vars = HashMap<Int, Record>().apply {
                put(1, ValueRecord(2))
            },
            parent = level1
        )
        
        val level3 = Vars(
            vars = HashMap<Int, Record>().apply {
                put(2, ValueRecord(3))
            },
            parent = level2
        )
        
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
        val parent = Vars(
            vars = HashMap<Int, Record>().apply {
                put(0, ValueRecord(100))
            },
            parent = null
        )
        
        val child = createVars(listOf(1, 2), parent = parent)
        
        assertEquals(100, child.get(0).getInt())
        assertEquals(EmptyRecord, child.get(1))
        assertEquals(EmptyRecord, child.get(2))
    }
}

