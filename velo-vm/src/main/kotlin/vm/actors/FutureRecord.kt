package vm.actors

import vm.Record

/**
 * In-flight result of an asynchronous cross-actor call.
 *
 * Created by `ActorCall` (the opcode emitted from `async receiver.method()`)
 * and consumed by `FutureAwait` (emitted from `await futureExpr`). Holds
 * the still-pending [Promise] together with a strong reference to
 * the [ActorHandle] that is responsible for completing it.
 *
 * Lifetime: holding the [ActorHandle] keeps the producing actor reachable so
 * the future can still be completed. Actors are not auto-collected when
 * unreferenced (they shut down explicitly or at program exit), so a future is
 * never orphaned between `async` and `await`.
 *
 * Not exposed as a Velo property surface — the type [compiler.nodes.FutureType]
 * returns `null` from `prop`, so the only operation on a future is `await`.
 */
class FutureRecord(
    val handle: ActorHandle,
    val future: Promise<ActorResponse>,
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = this as T

    override fun toString(): String =
        "future@${handle.id}${if (future.isDone()) "[done]" else "[pending]"}"
}
