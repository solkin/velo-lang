package vm.actors

import vm.Record

/**
 * Reference to an object that lives inside an [ActorHandle]'s VMContext.
 *
 * Behaves like a typed remote handle: methods invoked on this record cross the
 * thread boundary via [ActorHandle.requestCall]. The referenced object never
 * leaves the actor's [vm.MemoryArea], so the only thing crossing threads is the
 * lightweight tuple `(handle, objectId, className)` plus serialised arguments
 * and return values.
 *
 * Lifetime: this record pins nothing — actors are not auto-collected when
 * unreferenced; they shut down explicitly or at program exit. It simply holds
 * the [ActorHandle] so calls can be routed to it.
 *
 * [equals]/[hashCode] use logical identity (`handle` + `objectId`) so multiple
 * Velo references to the same internal object compare equal.
 */
class ActorRefRecord(
    val handle: ActorHandle,
    val objectId: Int,
    val className: String,
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = this as T

    override fun toString(): String = "actor[$className]#$objectId@${handle.id}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActorRefRecord) return false
        return handle === other.handle && objectId == other.objectId
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(handle) + objectId
}
