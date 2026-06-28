package vm2

/** Snapshot of heap activity, mirroring what the bench harness reports. */
data class MemoryStats(
    val allocations: Long,
    val liveCount: Int,
    val peakLive: Int,
    val collections: Int,
)

/**
 * The VM's allocation point for managed objects (arrays and class instances).
 *
 * The portable goal is a VM-*owned* heap with its own collector, so a host
 * without a GC (a C / WASM target) can still reclaim Velo garbage. On the JVM
 * the default [NoHeap] simply lets the host GC do the work; [ManagedHeap]
 * implements a real mark-sweep over VM roots and is the design proven here for
 * the eventual native port.
 */
interface Heap {
    /** Register a freshly allocated managed object and return it. */
    fun <T : Any> track(obj: T): T

    /** Collect if the allocation threshold has been crossed, marking from [roots]. */
    fun maybeCollect(roots: () -> Sequence<Any?>)

    fun stats(): MemoryStats
}

/** No tracking: allocations are plain host objects reclaimed by the host GC. */
object NoHeap : Heap {
    override fun <T : Any> track(obj: T): T = obj
    override fun maybeCollect(roots: () -> Sequence<Any?>) {}
    override fun stats() = MemoryStats(0, 0, 0, 0)
}

/**
 * A VM-owned heap with a stop-the-world **mark-sweep** collector.
 *
 * Every tracked object is held by the heap (so the host GC cannot reclaim it
 * early — the VM owns its lifetime). [maybeCollect] marks everything reachable
 * from the supplied roots — walking arrays, instances, scope chains and
 * captured closures — and sweeps the rest, so cyclic garbage is reclaimed too
 * (unlike refcounting). Collection runs every [threshold] allocations.
 *
 * Reachability through host-thread structures (actor mailboxes, future
 * continuations) is not visible here, so the caller must only collect when the
 * supplied roots are complete — i.e. a single in-flight fiber. With the reified
 * scheduler of the portable core this guard goes away.
 */
class ManagedHeap(private val threshold: Int = 100_000) : Heap {

    private val tracked: MutableSet<Any> = HashSet()
    private var allocations = 0L
    private var peakLive = 0
    private var collections = 0
    private var sinceCollect = 0

    override fun <T : Any> track(obj: T): T {
        tracked.add(obj)
        allocations++
        sinceCollect++
        if (tracked.size > peakLive) peakLive = tracked.size
        return obj
    }

    override fun maybeCollect(roots: () -> Sequence<Any?>) {
        if (sinceCollect < threshold) return
        collect(roots())
    }

    /** Force a collection now. Exposed for tests. */
    fun collect(roots: Sequence<Any?>) {
        sinceCollect = 0
        collections++
        val marked: MutableSet<Any> = HashSet()
        val work = ArrayDeque<Any?>()
        roots.forEach { work.addLast(it) }
        while (work.isNotEmpty()) {
            val v = work.removeLast() ?: continue
            if (!marked.add(v)) continue
            children(v, work)
        }
        // Sweep: drop every tracked object the mark phase did not reach.
        tracked.retainAll(marked)
    }

    /** Enqueue the references held by [v] so the mark phase can follow them. */
    private fun children(v: Any, work: ArrayDeque<Any?>) {
        when (v) {
            is VArray -> for (e in v.data) work.addLast(e)
            is Instance -> work.addLast(v.scope)
            // A Frame reached through the lexical/capture chain is marked for its
            // locals (its scope role) — its operand stack is only a root while the
            // frame is executing, which `rootsOf` covers via the call stack.
            is Frame -> { work.addLast(v.parent); for (e in v.localValues()) work.addLast(e) }
            is FuncValue -> work.addLast(v.captured)
            is BoxPtr -> work.addLast(v.get())
            is ArrayPtr -> work.addLast(v.get())
            is CallbackHandle -> work.addLast(v.fn.captured)
            is VFuture -> work.addLast(v.getNow())
        }
    }

    override fun stats() = MemoryStats(allocations, tracked.size, peakLive, collections)
}
