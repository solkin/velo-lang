package vm

import java.util.concurrent.atomic.AtomicInteger

interface Heap {
    fun put(value: Any): Int
    fun <T> get(id: Int): T
}

/**
 * Default implementation of Heap.
 * Each VM instance should have its own HeapImpl.
 */
class HeapImpl : Heap {

    private val enumerator = AtomicInteger()
    private val area = HashMap<Int, Any>()

    override fun put(value: Any): Int {
        val id = enumerator.getAndIncrement()
        area[id] = value
        return id
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(id: Int): T {
        return area[id] as T ?: throw Exception("Broken heap area link: $id")
    }

}
