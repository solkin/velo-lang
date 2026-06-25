package vm

import java.util.concurrent.atomic.AtomicLong

/**
 * Unified memory area for all reference types in the VM.
 * Replaces both Heap and Native area with a single storage.
 */
interface MemoryArea {
    fun put(value: Any): Int
    fun <T> get(id: Int): T
    fun release(id: Int)
    fun getStats(): MemoryStats
}

/**
 * Handle-table heap: a reference is a dense integer id indexing a flat slot
 * array, not a key into a `HashMap`. `get` is therefore a bounds-checked array
 * load — no `Integer` boxing, no hashing, no bucket walk — which matters because
 * every array element, class field and native handle access goes through it on
 * the hot path. Released ids are recycled via a free-list, which is what lets a
 * collector hand slots back (see [release]); until something calls `release`
 * the area only grows, exactly like before.
 *
 * Each VM context owns its own area, touched only by that actor's single active
 * thread, so the storage needs no synchronization. This is also the shape a
 * non-JVM port wants: an index into a VM-owned heap, relocatable because callers
 * hold the id, not the address.
 */
class MemoryAreaImpl : MemoryArea {

    private var slots = arrayOfNulls<Any?>(INITIAL_CAPACITY)
    private var top = 0                          // high-water id: every id < top is live or freed
    private var freeIds = IntArray(INITIAL_FREE) // stack of recycled ids
    private var freeCount = 0

    // Statistics
    private val allocations = AtomicLong(0)
    private val deallocations = AtomicLong(0)
    private var peakCount: Long = 0

    override fun put(value: Any): Int {
        val id = if (freeCount > 0) freeIds[--freeCount] else top++
        if (id >= slots.size) {
            var n = slots.size
            while (id >= n) n = n shl 1
            slots = slots.copyOf(n)
        }
        slots[id] = value
        allocations.incrementAndGet()
        val live = (top - freeCount).toLong()
        if (live > peakCount) peakCount = live
        return id
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(id: Int): T =
        (slots[id] ?: throw Exception("Broken memory area link: $id")) as T

    override fun release(id: Int) {
        if (id in 0 until top && slots[id] != null) {
            slots[id] = null
            if (freeCount >= freeIds.size) freeIds = freeIds.copyOf(freeIds.size shl 1)
            freeIds[freeCount++] = id
            deallocations.incrementAndGet()
        }
    }

    override fun getStats(): MemoryStats = MemoryStats(
        allocations = allocations.get(),
        deallocations = deallocations.get(),
        activeCount = (top - freeCount).toLong(),
        peakCount = peakCount,
    )

    private companion object {
        const val INITIAL_CAPACITY = 1 shl 12 // 4096 slots
        const val INITIAL_FREE = 64
    }
}

/**
 * Statistics about memory usage.
 */
data class MemoryStats(
    val allocations: Long,
    val deallocations: Long,
    val activeCount: Long,
    val peakCount: Long
)
