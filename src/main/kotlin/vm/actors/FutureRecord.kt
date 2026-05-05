package vm.actors

import vm.Record
import java.lang.ref.Cleaner
import java.util.concurrent.CompletableFuture

/**
 * In-flight result of an asynchronous cross-actor call.
 *
 * Created by `ActorCall` (the opcode emitted from `async receiver.method()`)
 * and consumed by `FutureAwait` (emitted from `await futureExpr`). Holds
 * the still-pending [CompletableFuture] together with a strong reference to
 * the [ActorHandle] that is responsible for completing it.
 *
 * Lifetime: the constructor increments [ActorHandle.refCount] and registers
 * a [Cleaner] action that decrements it when this record becomes
 * unreachable. The actor therefore stays alive for as long as any future
 * pointing into it is reachable, even if no [ActorRefRecord] for that actor
 * is reachable any more — otherwise the worker could shut down between
 * `async` and `await`, leaving the future to hang forever.
 *
 * Not exposed as a Velo property surface — the type [compiler.nodes.FutureType]
 * returns `null` from `prop`, so the only operation on a future is `await`.
 */
class FutureRecord(
    val handle: ActorHandle,
    val future: CompletableFuture<ActorResponse>,
) : Record {

    init {
        handle.refCount.incrementAndGet()
        cleaner.register(this, ReleaseTask(handle))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = this as T

    override fun toString(): String =
        "future@${handle.id}${if (future.isDone) "[done]" else "[pending]"}"

    companion object {
        private val cleaner: Cleaner = Cleaner.create()
    }

    /** Static class so the [Cleaner] action does not capture the [FutureRecord]. */
    private class ReleaseTask(private val handle: ActorHandle) : Runnable {
        override fun run() {
            handle.releaseRef()
        }
    }
}
