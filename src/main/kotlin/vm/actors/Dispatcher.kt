package vm.actors

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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
