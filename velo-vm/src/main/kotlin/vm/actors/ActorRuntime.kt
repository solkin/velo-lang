package vm.actors

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide registry of live actors.
 *
 * Owned by the host [vm.VMContext]; shared by reference with every actor
 * thread spawned from that context, so different actors can hold each
 * other's handles. The registry exists to:
 *
 *  - hand out monotonically-increasing actor ids for diagnostics;
 *  - enable a deterministic "shut everything down" hook on program exit
 *    (used by [vm.VM.run]'s `finally`), independent of the JVM's `Cleaner`
 *    which is best-effort.
 *
 * Lifetime model: the registry holds a strong reference to each live
 * [ActorHandle] until its worker thread exits. GC-driven shutdown still
 * works because no Velo code retains a reference: when the last
 * [ActorRefRecord] becomes unreachable, the handle's [Cleaner] action
 * decrements its refcount, which posts `Shutdown` to the worker mailbox.
 * The worker drains, exits, and calls [unregister] from its `finally`
 * block, releasing the registry's last reference.
 */
class ActorRuntime(
    /** Placement strategy for spawned actors — thread-per-actor by default,
     *  or a host-injected pooled backend (VEL-17). */
    private val dispatcherFactory: DispatcherFactory = ThreadPerActorFactory,
) {

    private val nextId = java.util.concurrent.atomic.AtomicInteger(0)
    private val handles = ConcurrentHashMap<Int, ActorHandle>()
    private val fatalRef = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)

    /**
     * Count of Velo callbacks a host has retained to fire later. A native that
     * keeps a [VeloFunctionImpl] for async firing retains it (and releases when
     * done); while any are outstanding the CLI event loop ([vm.VM.run]) stays
     * alive even with an empty mailbox, so the eventual callback is delivered.
     * Touched from arbitrary host threads, hence atomic.
     */
    private val hostCallbacks = java.util.concurrent.atomic.AtomicInteger(0)

    fun retainCallback() { hostCallbacks.incrementAndGet() }
    fun releaseCallback() { hostCallbacks.decrementAndGet() }
    fun hasHostCallbacks(): Boolean = hostCallbacks.get() > 0

    /**
     * Invoked once when the first fatal failure is raised. Used by embedded
     * hosts (`VeloRuntime.start`) to report and stop the program; in CLI mode
     * the main pump polls [fatalOrNull] instead and rethrows on its own thread.
     */
    @Volatile
    var onFatal: ((Throwable) -> Unit)? = null

    fun nextActorId(): Int = nextId.getAndIncrement()

    /**
     * Create the [Dispatcher] for a freshly spawned actor, per the configured
     * placement strategy. The main context does not use this — its dispatcher
     * is supplied by the host.
     */
    fun newActorDispatcher(name: String): Dispatcher = dispatcherFactory.create(name)

    /**
     * Record an unrecoverable failure that has no awaiting future to surface
     * through — e.g. a fire-and-forget callback that threw. First failure
     * wins; the program terminates loudly instead of corrupting silently.
     */
    fun raiseFatal(ex: Throwable) {
        if (fatalRef.compareAndSet(null, ex)) {
            onFatal?.invoke(ex)
        }
    }

    fun fatalOrNull(): Throwable? = fatalRef.get()

    /**
     * True when any live actor has a fiber parked on an `await`. The cooperative
     * event loop ([vm.VM.run]) stays alive while this holds, so a suspended
     * computation on any actor — not just main — still gets resumed.
     */
    fun anyParkedFibers(): Boolean {
        for (handle in handles.values) if (handle.hasParkedFibers()) return true
        return false
    }

    fun register(handle: ActorHandle) {
        handles[handle.id] = handle
    }

    fun unregister(id: Int) {
        handles.remove(id)
    }

    /**
     * Cooperatively stop every live actor. Used by [vm.VM] on program
     * exit to ensure pending requests drain before the JVM shuts down.
     */
    fun shutdownAll() {
        // Snapshot via the weakly-consistent iterator rather than a pre-sized
        // copy (`toList`): actors unregister themselves concurrently as they
        // finish shutting down, and a pre-sized copy can throw
        // NoSuchElementException when the map shrinks mid-iteration. A plain
        // for-each over a ConcurrentHashMap view never throws under concurrent
        // modification.
        val live = ArrayList<ActorHandle>()
        for (handle in handles.values) live.add(handle)
        for (handle in live) handle.requestShutdown()
        // Release the shared actor pool, if any (no-op for thread-per-actor).
        // Graceful: the Shutdown tasks just posted drain first.
        dispatcherFactory.shutdown()
    }
}
