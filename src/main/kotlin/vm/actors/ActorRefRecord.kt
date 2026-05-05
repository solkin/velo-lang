package vm.actors

import vm.Record
import java.lang.ref.Cleaner

/**
 * Reference to an object that lives inside an [ActorHandle]'s VMContext.
 *
 * Behaves like a typed remote handle: methods invoked on this record cross the
 * thread boundary via [ActorHandle.requestCall]. The referenced object never
 * leaves the actor's [vm.MemoryArea], so the only thing crossing threads is the
 * lightweight tuple `(handle, objectId, className)` plus serialised arguments
 * and return values.
 *
 * Lifetime: each [ActorRefRecord] increments [ActorHandle.refCount] in its
 * constructor and registers a [Cleaner] action that decrements it when the
 * record is collected. When the count drops to zero the actor cooperatively
 * shuts down. The [Cleaner] action deliberately holds only [ActorHandle], not
 * `this`, so the record itself stays GC-eligible.
 *
 * [equals]/[hashCode] use logical identity (`handle` + `objectId`) so multiple
 * Velo references to the same internal object compare equal, even though each
 * JVM instance has its own cleaner registration.
 */
class ActorRefRecord(
    val handle: ActorHandle,
    val objectId: Int,
    val className: String,
) : Record {

    init {
        handle.refCount.incrementAndGet()
        cleaner.register(this, ReleaseTask(handle))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = this as T

    override fun toString(): String = "actor[$className]#$objectId@${handle.id}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActorRefRecord) return false
        return handle === other.handle && objectId == other.objectId
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(handle) + objectId

    companion object {
        private val cleaner: Cleaner = Cleaner.create()
    }

    /** Static class so the [Cleaner] action doesn't capture the [ActorRefRecord]. */
    private class ReleaseTask(private val handle: ActorHandle) : Runnable {
        override fun run() {
            handle.releaseRef()
        }
    }
}
