package vm.actors

/**
 * The VM's own one-shot future — replaces `java.util.concurrent.CompletableFuture`
 * for actor responses so the core does not lean on the JDK's concurrency library.
 *
 * It resolves exactly once with a value; actor failures are encoded as
 * [ActorResponse.Failure] (never thrown), so there is no exceptional completion
 * to model. The two ways to observe it:
 *   - [onComplete] — register a continuation, the cooperative path (an awaiting
 *     fiber parks and resumes here); runs immediately if already resolved.
 *   - [await] — block the calling thread until resolved; only the nested/inline
 *     and host-thread-backend paths use it (the cooperative loop never blocks).
 *
 * In cooperative mode every resolve/observe happens on the single loop thread,
 * so the monitor is uncontended; it is here only to stay correct when an
 * optional host thread backend resolves from a worker while another actor
 * registers a continuation. A non-JVM port maps the monitor to its own
 * mutex/condvar, or drops it entirely for a single-threaded build.
 */
class Promise<T> {

    private val lock = Object()
    private var resolved = false
    private var result: T? = null
    private var waiters: MutableList<(T) -> Unit>? = null

    fun isDone(): Boolean = synchronized(lock) { resolved }

    /** Resolve with [value]; the first call wins, later calls are ignored. */
    fun resolve(value: T) {
        val toRun: List<(T) -> Unit>
        synchronized(lock) {
            if (resolved) return
            resolved = true
            result = value
            toRun = waiters ?: emptyList()
            waiters = null
            lock.notifyAll()
        }
        // Run continuations outside the lock so they can register/resolve freely.
        for (w in toRun) w(value)
    }

    /** Run [action] when resolved — immediately if it already is. */
    fun onComplete(action: (T) -> Unit) {
        synchronized(lock) {
            if (!resolved) {
                (waiters ?: mutableListOf<(T) -> Unit>().also { waiters = it }).add(action)
                return
            }
        }
        @Suppress("UNCHECKED_CAST")
        action(result as T)
    }

    /** Block the calling thread until resolved and return the value. */
    @Suppress("UNCHECKED_CAST")
    fun await(): T = synchronized(lock) {
        while (!resolved) lock.wait()
        result as T
    }
}
