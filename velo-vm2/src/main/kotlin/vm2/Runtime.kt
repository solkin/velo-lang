package vm2

import core.Dispatcher
import core.DispatcherFactory
import java.util.concurrent.LinkedBlockingQueue

/**
 * The runtime's central event loop, pumped on the thread that calls
 * `VeloRuntime.run`. It backs the *main* actor and, in the portable
 * no-threads mode, every spawned actor too. Cross-thread completions (from
 * host-threaded actor dispatchers) post resume tasks here and are picked up by
 * the blocking [run] pump.
 */
class EventLoop {
    private val queue = LinkedBlockingQueue<Runnable>()
    @Volatile private var stopped = false

    fun post(task: Runnable) = queue.put(task)

    fun run() {
        while (!stopped) {
            val task = queue.take()
            task.run()
        }
    }

    fun stop() {
        stopped = true
        queue.put(Runnable {}) // wake the pump so it observes `stopped`
    }
}

/** A [Dispatcher] that serialises onto a shared [EventLoop] (the cooperative default). */
class LoopDispatcher(private val loop: EventLoop) : Dispatcher {
    override fun execute(task: Runnable) = loop.post(task)
    override fun close() {}
    override fun joinFor(timeoutMs: Long) {}
    override fun isAlive() = true
}

/**
 * A future for a cross-actor (or value-returning callback) result. Thread-safe
 * so a host-threaded actor can complete (or fail) one the main loop is awaiting.
 * Continuations registered after settlement fire immediately. A failure carries
 * the originating actor's error so it surfaces at the `await` that drains it.
 */
class VFuture {
    private val lock = Any()
    private var done = false
    private var value: Any? = null
    private var error: Throwable? = null
    private val waiters = ArrayList<(Any?, Throwable?) -> Unit>()

    val isDone: Boolean get() = synchronized(lock) { done }
    fun getNow(): Any? = synchronized(lock) { value }
    fun errorNow(): Throwable? = synchronized(lock) { error }

    fun complete(result: Any?) = settle(result, null)
    fun fail(cause: Throwable) = settle(null, cause)

    private fun settle(result: Any?, cause: Throwable?) {
        val toRun: List<(Any?, Throwable?) -> Unit>
        synchronized(lock) {
            if (done) return
            done = true
            value = result
            error = cause
            toRun = ArrayList(waiters)
            waiters.clear()
        }
        toRun.forEach { it(result, cause) }
    }

    fun onComplete(cb: (Any?, Throwable?) -> Unit) {
        val ready: Boolean
        val v: Any?
        val e: Throwable?
        synchronized(lock) {
            ready = done
            v = value
            e = error
            if (!done) waiters.add(cb)
        }
        if (ready) cb(v, e)
    }
}

/**
 * A handle a host holds to keep the program's event loop alive past the main
 * fiber — while a UI screen is shown, a subscription is registered, etc. Each
 * [retain] must be balanced by a [release]; once nothing is retained (and the
 * main fiber is done with no actor work pending) the loop stops.
 */
interface LoopHandle {
    fun retain()
    fun release()
}

/** A Velo runtime failure — an uncaught error inside guest code (optionally actor-tagged). */
class VeloError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Handle to a program launched non-blocking with `VeloRuntime.start`. The event
 * loop runs on a runtime-owned thread; the host keeps its own thread free (e.g.
 * a UI thread that injected itself as the main dispatcher). Use [stop] to tear
 * the program down and [awaitTermination] to wait for it to finish on its own.
 */
class ProgramHandle internal constructor(
    private val loop: EventLoop,
    private val mainDispatcher: Dispatcher?,
    private val factory: DispatcherFactory?,
    private val pumpThread: Thread,
    private val error: () -> Throwable?,
) {
    fun isAlive(): Boolean = pumpThread.isAlive
    fun awaitTermination(timeoutMs: Long) = pumpThread.join(timeoutMs)
    fun error(): Throwable? = error.invoke()

    fun stop() {
        loop.stop()
        mainDispatcher?.close()
        factory?.shutdown()
    }
}

/**
 * A spawned actor: its instance (the constructed class scope) and the serial
 * [dispatcher] every message and resumed continuation for it runs on. [name] is
 * the actor class name, used to tag failures that escape its methods.
 */
class ActorRef(val instance: Instance, val dispatcher: Dispatcher, val name: String)

/**
 * A function value pinned to the actor that owns it ([owner]). Produced when a
 * `FuncValue` crosses an actor boundary: invoking it does not run the closure
 * locally — it ships the (transferred) arguments to the owner's dispatcher.
 * A void callback is fire-and-forget; a value-returning one parks the caller
 * until the owner replies.
 */
class CallbackHandle(val owner: ActorRef, val fn: FuncValue)
