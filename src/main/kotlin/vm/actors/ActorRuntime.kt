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

    fun nextActorId(): Int = nextId.getAndIncrement()

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
