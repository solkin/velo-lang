package vm

import java.util.concurrent.atomic.AtomicLong

/**
 * Unified memory area for all reference types in the VM.
 *
 * A reference is a dense integer id; the area maps each id to its value. Beyond
 * put/get/release it exposes a minimal mark-sweep surface ([shouldCollect],
 * [clearMarks], [mark], [sweep], [noteCollected]) that a precise collector
 * ([HeapCollector]) drives to reclaim unreachable slots. Implementations that do
 * not collect inherit the no-op defaults and simply grow.
 */
interface MemoryArea {
    fun put(value: Any): Int
    fun <T> get(id: Int): T
    fun release(id: Int)
    fun getStats(): MemoryStats

    /** True when the live set has grown past the collection threshold. */
    fun shouldCollect(): Boolean = false

    /** Clear all marks before a tracing pass. */
    fun clearMarks() {}

    /** Mark slot [id] reachable; returns true if newly marked (caller recurses). */
    fun mark(id: Int): Boolean = false

    /** Free every occupied-but-unmarked slot after a tracing pass. */
    fun sweep() {}

    /** Recompute the next collection threshold from the surviving live set. */
    fun noteCollected() {}
}

/**
 * Handle-table heap: a reference is a dense integer id indexing a flat slot
 * array, not a key into a `HashMap`. `get` is therefore a bounds-checked array
 * load — no `Integer` boxing, no hashing — which matters because every array
 * element, class field and native handle access goes through it on the hot path.
 *
 * Reclamation is a precise stop-the-world mark-sweep ([HeapCollector] supplies
 * the tracing; this class owns the slots, the parallel mark bits and the
 * free-list). Because each VM context owns its own area, touched only by that
 * actor's single active thread, the collector never coordinates across threads —
 * the actor model turns GC into N independent single-threaded collectors. The
 * collection threshold tracks the surviving live set (collect when occupancy
 * doubles past it), so churn stays bounded while genuinely-retaining programs
 * collect proportionally less often.
 */
class MemoryAreaImpl : MemoryArea {

    private var slots = arrayOfNulls<Any?>(INITIAL_CAPACITY)
    private var marks = BooleanArray(INITIAL_CAPACITY)
    private var top = 0                          // high-water id: every id < top is live or freed
    private var freeIds = IntArray(INITIAL_FREE) // stack of recycled ids
    private var freeCount = 0
    private var gcThreshold = THRESHOLD

    // Statistics
    private val allocations = AtomicLong(0)
    private val deallocations = AtomicLong(0)
    private var peakCount: Long = 0

    override fun put(value: Any): Int {
        val id = if (freeCount > 0) freeIds[--freeCount] else top++
        if (id >= slots.size) grow(id)
        slots[id] = value
        allocations.incrementAndGet()
        val live = (top - freeCount).toLong()
        if (live > peakCount) peakCount = live
        return id
    }

    private fun grow(id: Int) {
        var n = slots.size
        while (id >= n) n = n shl 1
        slots = slots.copyOf(n)
        marks = marks.copyOf(n)
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

    override fun shouldCollect(): Boolean = (top - freeCount) >= gcThreshold

    override fun clearMarks() = java.util.Arrays.fill(marks, 0, top, false)

    override fun mark(id: Int): Boolean {
        if (marks[id]) return false
        marks[id] = true
        return true
    }

    override fun sweep() {
        for (id in 0 until top) {
            if (slots[id] != null && !marks[id]) release(id)
        }
    }

    override fun noteCollected() {
        gcThreshold = maxOf(THRESHOLD, (top - freeCount) * 2)
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
        /** Min live-slot count that triggers a collection; tunable for experiments. */
        val THRESHOLD: Int = System.getProperty("velo.gc.threshold")?.toIntOrNull() ?: (1 shl 13) // 8192
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
