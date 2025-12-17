package vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeapTest {

    @Test
    fun `put and get returns same value`() {
        val heap = HeapImpl()
        val id = heap.put("test")
        assertEquals("test", heap.get<String>(id))
    }

    @Test
    fun `put and get with different types`() {
        val heap = HeapImpl()
        val id1 = heap.put(42)
        val id2 = heap.put("hello")
        val id3 = heap.put(3.14f)
        
        assertEquals(42, heap.get<Int>(id1))
        assertEquals("hello", heap.get<String>(id2))
        assertEquals(3.14f, heap.get<Float>(id3))
    }

    @Test
    fun `multiple puts return different ids`() {
        val heap = HeapImpl()
        val id1 = heap.put("first")
        val id2 = heap.put("second")
        val id3 = heap.put("third")
        
        assertEquals("first", heap.get<String>(id1))
        assertEquals("second", heap.get<String>(id2))
        assertEquals("third", heap.get<String>(id3))
    }

    @Test
    fun `get with invalid id throws exception`() {
        val heap = HeapImpl()
        assertFailsWith<Exception> {
            heap.get<String>(999)
        }
    }

    @Test
    fun `put array and retrieve it`() {
        val heap = HeapImpl()
        val array = arrayOf(1, 2, 3)
        val id = heap.put(array)
        val retrieved = heap.get<Array<Int>>(id)
        assertEquals(array.size, retrieved.size)
        assertEquals(1, retrieved[0])
        assertEquals(2, retrieved[1])
        assertEquals(3, retrieved[2])
    }

    @Test
    fun `put complex object`() {
        data class TestData(val x: Int, val y: String)
        val heap = HeapImpl()
        val data = TestData(42, "test")
        val id = heap.put(data)
        val retrieved = heap.get<TestData>(id)
        assertEquals(42, retrieved.x)
        assertEquals("test", retrieved.y)
    }

    @Test
    fun `sequential ids are incremental`() {
        val heap = HeapImpl()
        val id1 = heap.put("a")
        val id2 = heap.put("b")
        val id3 = heap.put("c")
        
        assertEquals(id1 + 1, id2)
        assertEquals(id2 + 1, id3)
    }
}

