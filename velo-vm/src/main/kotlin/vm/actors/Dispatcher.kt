package vm.actors

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serial execution domain an actor runs on.
 *
 * The contract is the same as a single-threaded executor:
 *   - tasks run one at a time, in submission order;
 *   - there is a happens-before edge between consecutive tasks.
 *
 * The default implementation is [ThreadDispatcher] — a dedicated daemon
 * thread per actor, preserving the original worker-thread semantics. The
 * program's main context runs on a dispatcher too: [PumpDispatcher] when the
 * host blocks in [vm.VM.run], or any host-provided serial executor (Android
 * main-looper Handler, Swing EDT, a single-threaded pool) when the program
 * is embedded via `VeloRuntime.start`. That choice is what pins Velo
 * callbacks to the host's main thread.
 */
interface Dispatcher {

    /** Enqueue [task] for serial execution. Must never run it inline. */
    fun execute(task: Runnable)

    /**
     * Ask the dispatcher to stop once already-submitted tasks have drained.
     * Tasks submitted after close may be silently dropped. Idempotent.
     */
    fun close() {}

    /** Wait at most [timeoutMs] for the dispatcher to finish. Used by tests. */
    fun joinFor(timeoutMs: Long) {}

    /** Whether the dispatcher can still execute tasks. */
    fun isAlive(): Boolean = true

    companion object {
        /**
         * Adapt any host serial executor (Android Handler::post,
         * SwingUtilities::invokeLater, a single-threaded pool) into a
         * [Dispatcher]. The host side is responsible for the serial
         * guarantee; close/join are host-managed and therefore no-ops.
         */
        fun from(executor: java.util.concurrent.Executor): Dispatcher =
            object : Dispatcher {
                override fun execute(task: Runnable) = executor.execute(task)
            }
    }
}

/**
 * Default actor dispatcher: one dedicated daemon thread draining an
 * unbounded task queue — the direct successor of the original per-actor
 * worker loop.
 *
 * [close] lets the thread drain everything already queued and then exit,
 * matching the old "process remaining mailbox, then return" shutdown
 * behaviour. Uncaught task failures are printed but do not kill the thread:
 * actor request handlers translate their own failures into responses, so a
 * throw here is a VM bug, and a half-dead silent actor would be worse than
 * a noisy one.
 */
class ThreadDispatcher(name: String) : Dispatcher {

    private val tasks = LinkedBlockingQueue<Runnable>()

    @Volatile
    private var closed = false

    private val thread: Thread = Thread({ loop() }, name).apply {
        isDaemon = true
        start()
    }

    private fun loop() {
        while (true) {
            val task = if (closed) tasks.poll() ?: return else tasks.take()
            try {
                task.run()
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
    }

    override fun execute(task: Runnable) {
        tasks.put(task)
    }

    override fun close() {
        closed = true
        tasks.put(Runnable {}) // wake the thread so it can observe `closed`
    }

    override fun joinFor(timeoutMs: Long) = thread.join(timeoutMs)

    override fun isAlive(): Boolean = thread.isAlive
}

/**
 * Dispatcher for the main context in CLI mode: tasks are queued by anyone,
 * executed only by the single thread that called [pump] — the thread that
 * entered [vm.VM.run]. This is the program's event loop.
 *
 * [pump] returns when the queue is empty and [idle] reports there is nothing
 * left to wait for (no live callback pins). While waiting on pins it
 * periodically nudges the GC so `Cleaner`-driven pin releases (dropped
 * callbacks, dead actors) are observed promptly instead of hanging the
 * program until an arbitrary future collection.
 *
 * Task failures propagate out of [pump] to the caller — for the main
 * context that is exactly the old `VM.run` behaviour where the main frame's
 * exception reached the top-level handler. [fatal] is checked between tasks
 * so failures raised by fire-and-forget callbacks on *other* actors also
 * terminate the program loudly.
 */
class PumpDispatcher : Dispatcher {

    private val tasks = LinkedBlockingQueue<Runnable>()

    override fun execute(task: Runnable) {
        tasks.put(task)
    }

    fun pump(fatal: () -> Throwable?, idle: () -> Boolean) {
        var idlePolls = 0
        while (true) {
            fatal()?.let { throw it }
            var task = tasks.poll()
            if (task == null) {
                if (idle()) return
                task = tasks.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS)
                if (task == null) {
                    idlePolls++
                    // Stale pins (dropped callbacks awaiting collection) are
                    // the common reason to be idle, so nudge early once, then
                    // back off to a slow heartbeat for genuinely-waiting
                    // programs (a host that will fire a callback eventually).
                    if (idlePolls == FIRST_GC_NUDGE_POLLS || idlePolls % GC_NUDGE_POLLS == 0) {
                        System.gc()
                    }
                    continue
                }
            }
            idlePolls = 0
            task.run()
        }
    }

    private companion object {
        const val IDLE_POLL_MS = 50L
        const val FIRST_GC_NUDGE_POLLS = 3
        const val GC_NUDGE_POLLS = 20
    }
}

/**
 * Strategy for giving each *spawned* actor its [Dispatcher] (VEL-17). The
 * default dedicates a daemon thread per actor ([ThreadPerActorFactory]);
 * [PooledDispatcherFactory] multiplexes every actor onto one shared bounded
 * pool. The program's main context never goes through a factory — its
 * dispatcher is supplied by the host ([PumpDispatcher] or [Dispatcher.from]).
 *
 * One factory backs one program run: [shutdown] is called from
 * [ActorRuntime.shutdownAll] to release shared resources (the pool).
 */
interface DispatcherFactory {
    fun create(name: String): Dispatcher

    /** Release shared resources (e.g. a thread pool) at program shutdown. */
    fun shutdown() {}
}

/**
 * Default placement: a dedicated [ThreadDispatcher] (daemon thread) per actor.
 * Stateless, so a single shared instance is reused.
 */
object ThreadPerActorFactory : DispatcherFactory {
    override fun create(name: String): Dispatcher = ThreadDispatcher(name)
}

/**
 * Multiplexes every spawned actor onto one shared, bounded daemon thread pool
 * instead of a thread per actor (VEL-17). Suited to memory-constrained hosts
 * (mobile), where a program with dozens of actors would otherwise mean dozens
 * of OS threads — each ~0.5–1 MB of stack plus scheduler load.
 *
 * Correctness rests on VEL-11: a parked `await` releases its pool thread, so a
 * suspended actor costs nothing here. A *blocking* call inside an actor (a
 * blocking native, `Time.sleep`, an inline value-returning callback) still
 * occupies a pool thread for its duration, so size [parallelism] with enough
 * headroom that simultaneously-blocked actors cannot starve the pool.
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
