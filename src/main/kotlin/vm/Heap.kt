package vm

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

interface Heap {
    fun put(value: Any): Int
    fun <T> get(id: Int): T
    fun release(id: Int)
    fun getStats(): HeapStats
}

/**
 * Default implementation of Heap with statistics tracking.
 * Each VM instance should have its own HeapImpl.
 */
class HeapImpl : Heap {

    private val enumerator = AtomicInteger()
    private val area = HashMap<Int, Any>()
    
    // Statistics
    private val allocations = AtomicLong(0)
    private val deallocations = AtomicLong(0)
    private var peakCount: Long = 0

    override fun put(value: Any): Int {
        val id = enumerator.getAndIncrement()
        area[id] = value
        allocations.incrementAndGet()
        
        val currentCount = area.size.toLong()
        if (currentCount > peakCount) {
            peakCount = currentCount
        }
        
        return id
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(id: Int): T {
        return area[id] as T ?: throw Exception("Broken heap area link: $id")
    }
    
    override fun release(id: Int) {
        if (area.remove(id) != null) {
            deallocations.incrementAndGet()
        }
    }
    
    override fun getStats(): HeapStats {
        return HeapStats(
            allocations = allocations.get(),
            deallocations = deallocations.get(),
            activeCount = area.size.toLong(),
            peakCount = peakCount
        )
    }
}
