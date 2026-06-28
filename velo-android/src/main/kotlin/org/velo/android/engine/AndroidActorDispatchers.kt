package org.velo.android.engine

import core.Dispatcher
import core.DispatcherFactory
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android-side actor placement: spawned Velo actors run on a small daemon pool instead
 * of sharing the program's cooperative pump. That keeps UI callbacks responsive when an
 * actor performs blocking host work such as Time.sleep.
 */
class AndroidActorDispatcherFactory(
    parallelism: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(2),
) : DispatcherFactory {

    private val threadCounter = AtomicInteger(0)

    private val pool: ExecutorService =
        Executors.newFixedThreadPool(parallelism.coerceAtLeast(1)) { runnable ->
            Thread(runnable, "velo-android-actor-${threadCounter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }

    override fun create(name: String): Dispatcher = AndroidActorDispatcher(pool)

    override fun shutdown() {
        pool.shutdown()
    }
}

private class AndroidActorDispatcher(private val pool: Executor) : Dispatcher {

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
        } catch (_: RejectedExecutionException) {
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
