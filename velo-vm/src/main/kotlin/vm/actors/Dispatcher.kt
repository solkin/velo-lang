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
 * The VM core provides only the cooperative [PumpDispatcher] (the program's
 * event loop) and the [CooperativeDispatcherFactory] that runs every actor on
 * it — no threads. A host may supply real-thread dispatchers (the CLI's
 * thread-per-actor or pooled backends) for parallelism, and the program's main
 * context can run on any host serial executor (Android main-looper Handler,
 * Swing EDT) adapted via [from]. That choice is what pins Velo callbacks to the
 * host's main thread.
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
 * Dispatcher for the main context in CLI mode: tasks are queued by anyone,
 * executed only by the single thread that called [pump] — the thread that
 * entered [vm.VM.run]. This is the program's event loop, and (with the
 * cooperative default) the one every spawned actor runs on too.
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
 * Strategy for giving each *spawned* actor its [Dispatcher].
 *
 * The VM core's default is cooperative ([CooperativeDispatcherFactory]: every
 * actor runs on the program's single event loop, no threads). A host injects a
 * thread backend — the CLI's thread-per-actor or pooled factory — for real
 * multicore parallelism; those live in the host, so the VM core stays
 * thread-agnostic. The program's main context never goes through a factory: its
 * dispatcher is supplied directly (a [PumpDispatcher] or [Dispatcher.from]).
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
 * Placement for a runtime that has not been wired for actor execution — a bare
 * [ActorRuntime] built outside [vm.VM.run] / [vm.VeloRuntime] (e.g. a unit-test
 * context that never spawns). Spawning an actor through it is a configuration
 * error rather than a silent wrong default.
 */
object UnconfiguredPlacement : DispatcherFactory {
    override fun create(name: String): Dispatcher =
        error("actor placement not configured — run the program via VM.run or VeloRuntime")
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
