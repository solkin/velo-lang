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
 * left to wait for (no parked fibers on any actor, no host-retained callbacks).
 * While not idle but momentarily empty it waits briefly for the next post — a
 * host callback fired from another thread, or a fiber resumed by one.
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
        while (true) {
            fatal()?.let { throw it }
            var task = tasks.poll()
            if (task == null) {
                if (idle()) return
                // Not idle but nothing queued: a parked fiber or a host-retained
                // callback may still produce work. Wait briefly for the next
                // post, then re-check fatal/idle.
                task = tasks.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS) ?: continue
            }
            task.run()
        }
    }

    private companion object {
        const val IDLE_POLL_MS = 50L
    }
}

/**
 * Host SPI for giving each *spawned* actor its [Dispatcher] (VEL-17). The
 * default dedicates a daemon thread per actor ([ThreadPerActorFactory]); a host
 * may instead inject a backend that multiplexes every actor onto a shared
 * bounded pool (the JVM `PooledDispatcherFactory` lives in the CLI host, not
 * here — the VM stays thread-agnostic). The program's main context never goes
 * through a factory — its dispatcher is supplied by the host ([PumpDispatcher]
 * or [Dispatcher.from]).
 *
 * One factory backs one program run: [shutdown] is called from
 * [ActorRuntime.shutdownAll] to release shared resources (e.g. a pool).
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
 * Cooperative placement — the portable default for [vm.VM.run]. Every spawned
 * actor shares the program's single event loop ([dispatcher], the
 * [PumpDispatcher] driving the run), instead of getting its own thread: all
 * actors run as fibers multiplexed on one thread, with **no host threads at
 * all**. VEL-11 makes this work — a parked `await` frees the loop for other
 * actors. Real multicore parallelism is opt-in via a host backend (e.g. the
 * CLI's pool); cooperative is what runs everywhere, including single-threaded
 * web/WASM.
 */
class CooperativeDispatcherFactory(private val dispatcher: Dispatcher) : DispatcherFactory {
    override fun create(name: String): Dispatcher = dispatcher
}
