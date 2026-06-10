package vm.actors

import java.lang.ref.Cleaner

/**
 * Shared GC-driven pin machinery for everything that keeps an actor alive:
 * [ActorRefRecord], [FutureRecord], [CallbackRecord], [VeloFunction] and
 * in-flight [ActorValue.Callback] payloads.
 *
 * Each pinning object increments [ActorHandle.refCount] in its constructor
 * and registers a [Release] action here; when the object becomes
 * unreachable the count drops, and at zero the actor cooperatively shuts
 * down. One process-wide [Cleaner] (one daemon thread) serves all pin
 * types instead of one per record class.
 */
internal object Pins {

    val cleaner: Cleaner = Cleaner.create()

    /** Static action class so the cleaner never captures the pinning object. */
    class Release(private val handle: ActorHandle) : Runnable {
        override fun run() {
            handle.releaseRef()
        }
    }
}
