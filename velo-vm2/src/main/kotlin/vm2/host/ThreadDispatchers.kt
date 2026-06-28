package vm2.host

import core.Dispatcher
import core.DispatcherFactory
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host (JVM) actor placement: one daemon thread per actor draining its own
 * queue. Plug in via `VeloRuntime.actorPlacement { ThreadPerActorFactory() }`
 * to give each actor an OS thread; a suspended `await` releases the thread, a
 * blocking native holds it for its duration.
 *
 * These are host capabilities, deliberately outside the portable VM core — the
 * core only knows the [DispatcherFactory] SPI and runs cooperatively without one.
 */
class ThreadPerActorFactory : DispatcherFactory {
    private val counter = AtomicInteger(0)
    override fun create(name: String): Dispatcher = ThreadDispatcher("$name-${counter.getAndIncrement()}")
}

/** A single dedicated daemon thread draining a queue — usable as a host main dispatcher too. */
class ThreadDispatcher(name: String) : Dispatcher {
    private val tasks = LinkedBlockingQueue<Runnable>()
    @Volatile private var closed = false

    private val thread = Thread({ loop() }, "velo-actor-$name").apply {
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

    override fun execute(task: Runnable) { tasks.put(task) }
    override fun close() { closed = true; tasks.put(Runnable {}) }
    override fun joinFor(timeoutMs: Long) = thread.join(timeoutMs)
    override fun isAlive(): Boolean = thread.isAlive
}

/**
 * Host (JVM) actor placement multiplexing every actor onto one shared bounded
 * daemon pool. Each actor still runs serially (one task at a time, in order);
 * the next task is submitted only after the current finishes, so one busy actor
 * cannot monopolise a pool thread.
 */
class PooledDispatcherFactory(
    parallelism: Int = Runtime.getRuntime().availableProcessors(),
) : DispatcherFactory {
    private val counter = AtomicInteger(0)
    private val pool: ExecutorService =
        Executors.newFixedThreadPool(parallelism.coerceAtLeast(1)) { r ->
            Thread(r, "velo-actor-pool-${counter.getAndIncrement()}").apply { isDaemon = true }
        }

    override fun create(name: String): Dispatcher = PooledDispatcher(pool)
    override fun shutdown() { pool.shutdown() }
}

private class PooledDispatcher(private val pool: Executor) : Dispatcher {
    private val lock = Object()
    private val tasks = ArrayDeque<Runnable>()
    private var scheduled = false
    private var closed = false

    override fun execute(task: Runnable) {
        synchronized(lock) {
            if (closed) return
            tasks.addLast(task)
            scheduleLocked()
        }
    }

    private fun scheduleLocked() {
        if (scheduled || tasks.isEmpty()) return
        scheduled = true
        try {
            pool.execute(::runNext)
        } catch (ex: RejectedExecutionException) {
            scheduled = false
            tasks.clear()
            (lock as Object).notifyAll()
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
