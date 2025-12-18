package vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemoryAreaTest {

    @Test
    fun `put and get returns same value`() {
        val memory = MemoryAreaImpl()
        val id = memory.put("test")
        assertEquals("test", memory.get<String>(id))
    }

    @Test
    fun `put and get with different types`() {
        val memory = MemoryAreaImpl()
        val id1 = memory.put(42)
        val id2 = memory.put("hello")
        val id3 = memory.put(3.14f)
        
        assertEquals(42, memory.get<Int>(id1))
        assertEquals("hello", memory.get<String>(id2))
        assertEquals(3.14f, memory.get<Float>(id3))
    }

    @Test
    fun `multiple puts return different ids`() {
        val memory = MemoryAreaImpl()
        val id1 = memory.put("first")
        val id2 = memory.put("second")
        val id3 = memory.put("third")
        
        assertEquals("first", memory.get<String>(id1))
        assertEquals("second", memory.get<String>(id2))
        assertEquals("third", memory.get<String>(id3))
    }

    @Test
    fun `get with invalid id throws exception`() {
        val memory = MemoryAreaImpl()
        assertFailsWith<Exception> {
            memory.get<String>(999)
        }
    }

    @Test
    fun `put array and retrieve it`() {
        val memory = MemoryAreaImpl()
        val array = arrayOf(1, 2, 3)
        val id = memory.put(array)
        val retrieved = memory.get<Array<Int>>(id)
        assertEquals(array.size, retrieved.size)
        assertEquals(1, retrieved[0])
        assertEquals(2, retrieved[1])
        assertEquals(3, retrieved[2])
    }

    @Test
    fun `put complex object`() {
        data class TestData(val x: Int, val y: String)
        val memory = MemoryAreaImpl()
        val data = TestData(42, "test")
        val id = memory.put(data)
        val retrieved = memory.get<TestData>(id)
        assertEquals(42, retrieved.x)
        assertEquals("test", retrieved.y)
    }

    @Test
    fun `sequential ids are incremental`() {
        val memory = MemoryAreaImpl()
        val id1 = memory.put("a")
        val id2 = memory.put("b")
        val id3 = memory.put("c")
        
        assertEquals(id1 + 1, id2)
        assertEquals(id2 + 1, id3)
    }

    @Test
    fun `release removes object from memory`() {
        val memory = MemoryAreaImpl()
        val id = memory.put("test")
        
        assertEquals("test", memory.get<String>(id))
        
        memory.release(id)
        
        assertFailsWith<Exception> {
            memory.get<String>(id)
        }
    }

    @Test
    fun `getStats returns correct statistics`() {
        val memory = MemoryAreaImpl()
        
        val id1 = memory.put("a")
        val id2 = memory.put("b")
        memory.put("c")
        
        memory.release(id1)
        memory.release(id2)
        
        val stats = memory.getStats()
        assertEquals(3, stats.allocations)
        assertEquals(2, stats.deallocations)
        assertEquals(1, stats.activeCount)
        assertEquals(3, stats.peakCount)
    }
}
