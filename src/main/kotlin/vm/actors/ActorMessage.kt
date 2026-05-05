package vm.actors

import java.util.concurrent.CompletableFuture

/**
 * Cross-thread payload exchanged with actors via [ActorHandle].
 *
 * Values are intentionally restricted to forms that can be safely materialised
 * in a different VM context: primitives, host strings/booleans, recursive
 * containers and [ActorRefRecord]s pinned to the same actor as the receiver.
 *
 * The producer of a request is responsible for cloning each [Record] into one
 * of these forms before sending it; the receiver reconstructs records in its
 * own [vm.MemoryArea]. This way nothing leaks the original [vm.Record]
 * identity or the source [vm.MemoryArea] across threads.
 */
sealed class ActorValue {
    object Void : ActorValue()
    data class Primitive(val value: Any) : ActorValue()
    data class Array(val items: List<ActorValue>) : ActorValue()
    data class Dict(val entries: List<Pair<ActorValue, ActorValue>>) : ActorValue()

    /**
     * Cross-thread description of an `actor[T]` value. Carries only what's
     * needed to reconstruct an [ActorRefRecord] on the receiving side; never
     * the record itself, so the wire format does not incidentally inflate
     * [ActorHandle.refCount].
     */
    data class Ref(
        val handle: ActorHandle,
        val objectId: Int,
        val className: String,
    ) : ActorValue()
}

/**
 * One unit of work scheduled on an actor's event loop.
 *
 * [Construct] is sent exactly once at spawn time. [Call] is the typed remote
 * method invocation. [Shutdown] is the cooperative termination signal injected
 * by the runtime when an [ActorHandle] becomes unreachable; it carries no
 * payload because the sender does not wait for confirmation.
 */
sealed class ActorRequest {
    data class Construct(
        val classFrameNum: Int,
        val args: List<ActorValue>,
        val response: CompletableFuture<ActorResponse>,
    ) : ActorRequest()

    data class Call(
        val objectId: Int,
        val methodVarIndex: Int,
        val args: List<ActorValue>,
        val response: CompletableFuture<ActorResponse>,
    ) : ActorRequest()

    object Shutdown : ActorRequest()
}

/**
 * Reply to an [ActorRequest], delivered via the request's [CompletableFuture].
 *
 * For [Construct], a successful response carries the freshly assigned root
 * objectId. For [Call], it carries the method's return value (already
 * marshalled into [ActorValue]) — or [Failure] with a host-side message
 * describing the runtime/compile-time issue that prevented the call.
 */
sealed class ActorResponse {
    data class Constructed(val rootObjectId: Int) : ActorResponse()
    data class Returned(val value: ActorValue) : ActorResponse()
    data class Failure(val message: String) : ActorResponse()
}
