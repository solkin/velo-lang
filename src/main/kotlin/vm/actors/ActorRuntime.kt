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
class ActorRuntime {

    private val nextId = java.util.concurrent.atomic.AtomicInteger(0)
    private val handles = ConcurrentHashMap<Int, ActorHandle>()
    private val fatalRef = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)

    /**
     * Invoked once when the first fatal failure is raised. Used by embedded
     * hosts (`VeloRuntime.start`) to report and stop the program; in CLI mode
     * the main pump polls [fatalOrNull] instead and rethrows on its own thread.
     */
    @Volatile
    var onFatal: ((Throwable) -> Unit)? = null

    fun nextActorId(): Int = nextId.getAndIncrement()

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
        for (handle in handles.values.toList()) {
            handle.requestShutdown()
        }
    }
}
