package host

import vm.actors.Dispatcher
import vm.actors.DispatcherFactory
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host (JVM) actor-placement backend: multiplexes every spawned actor onto one
 * shared, bounded daemon thread pool instead of a thread per actor (VEL-17).
 *
 * This is a **host capability**, not part of the portable VM — the VM core only
 * knows the [DispatcherFactory] SPI and runs cooperatively without it. A host
 * with OS threads plugs this in to get real multicore parallelism; a host
 * without them (web/WASM) simply doesn't. Correctness rests on VEL-11: a parked
 * `await` releases its pool thread, so a suspended actor costs nothing. A
 * *blocking* call (a blocking native, `Time.sleep`, an inline value-returning
 * callback) still holds a pool thread for its duration, so size [parallelism]
 * with enough headroom that simultaneously-blocked actors cannot starve it.
 */
class PooledDispatcherFactory(
    parallelism: Int = Runtime.getRuntime().availableProcessors(),
) : DispatcherFactory {

    private val threadCounter = AtomicInteger(0)

    private val pool: ExecutorService =
        Executors.newFixedThreadPool(parallelism.coerceAtLeast(1)) { runnable ->
            Thread(runnable, "velo-actor-pool-${threadCounter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }

    override fun create(name: String): Dispatcher = PooledDispatcher(pool)

    override fun shutdown() {
        // Graceful: let already-queued teardown tasks finish; daemon threads
        // then exit. New submissions after this are rejected (handled by
        // PooledDispatcher.scheduleLocked).
        pool.shutdown()
    }
}

/**
 * A per-actor serial execution domain layered over a shared [pool] (VEL-17).
 *
 * Honours the full [Dispatcher] contract — tasks run one at a time, in
 * submission order, with a happens-before edge between them, and never inline —
 * but borrows a thread from [pool] for each task instead of owning one. Each
 * task runs as its own pool job and the next is submitted only after it
 * finishes, so a backlog on one actor cannot monopolise a pool thread while
 * other actors wait.
 */
internal class PooledDispatcher(private val pool: Executor) : Dispatcher {

    private val lock = Object()
    private val tasks = ArrayDeque<Runnable>()  // guarded by `lock`
    private var scheduled = false               // a drain job is queued/running — guarded by `lock`
    private var closed = false                  // guarded by `lock`

    override fun execute(task: Runnable) {
        synchronized(lock) {
            if (closed) return
            tasks.addLast(task)
            scheduleLocked()
        }
    }

    /** Submit a drain job unless one is already pending. Caller holds [lock]. */
    private fun scheduleLocked() {
        if (scheduled || tasks.isEmpty()) return
        scheduled = true
        try {
            pool.execute(::runNext)
        } catch (ex: RejectedExecutionException) {
            // Pool already shut down (program exiting): drop the backlog and
            // wake any joiner rather than leaving it blocked forever.
            scheduled = false
            tasks.clear()
            lock.notifyAll()
        }
    }

    private fun runNext() {
        val task = synchronized(lock) { tasks.removeFirstOrNull() }
        if (task != null) {
            try {
                task.run()
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
        synchronized(lock) {
            scheduled = false
            // Drain whatever is left (including tasks enqueued during this one);
            // `closed` only blocks *new* submissions, matching ThreadDispatcher's
            // drain-then-stop. When nothing remains, release any joiner.
            scheduleLocked()
            if (!scheduled) lock.notifyAll()
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            if (!scheduled) lock.notifyAll()
        }
    }

    override fun isAlive(): Boolean = synchronized(lock) { !closed }

    override fun joinFor(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (scheduled || tasks.isNotEmpty()) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) return
                lock.wait(remaining)
            }
        }
    }
}
