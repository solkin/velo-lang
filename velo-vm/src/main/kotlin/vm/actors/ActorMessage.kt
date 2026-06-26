package vm.actors

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

    /**
     * Cross-thread description of a `data class` value: the class it was built
     * from plus its field values in declaration order. The receiver rebuilds a
     * fresh, independent instance by re-running the class constructor with the
     * decoded fields — a deep copy, so no mutable state is aliased across the
     * boundary.
     */
    data class Data(val classFrameNum: Int, val fields: List<ActorValue>) : ActorValue()

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

    /**
     * Cross-thread description of a `func[(args) void]` value: the owning
     * handle plus the owner's [FuncRecord]. The record's captured `Vars`
     * are only ever touched by the owner's dispatcher, so carrying the
     * reference itself is safe.
     *
     * Unlike [Ref], this *does* pin the owner while in flight: the whole
     * point of handing out a callback is that the owner stays serviceable
     * until it is delivered. Without the pin, a main context could finish
     * its frame and stop pumping in the gap between `async` encoding the
     * callback and the receiving actor materialising a [CallbackRecord].
     * The pin is dropped by GC ([Pins]) once the payload itself is dropped;
     * the decoded record carries its own pin from construction.
     */
    class Callback(
        val handle: ActorHandle,
        val func: vm.records.FuncRecord,
    ) : ActorValue() {
        init {
            handle.refCount.incrementAndGet()
            Pins.cleaner.register(this, Pins.Release(handle))
        }
    }
}

/**
 * One unit of work scheduled on an actor's event loop.
 *
 * [Main] runs a whole program frame and is used only by the main context
 * (the program itself is "actor #0"); its failures either propagate to the
 * pumping thread (CLI) or go to [onFailure] (embedded hosts). [Call] is the
 * typed remote method invocation. [Shutdown] is the cooperative termination
 * signal injected by the runtime when an [ActorHandle] becomes unreachable;
 * it carries no payload because the sender does not wait for confirmation.
 * (Construction is synchronous and inline — see [ActorHandle.spawn] — so it
 * is not a request type.)
 */
sealed class ActorRequest {
    data class Main(
        val frameNum: Int,
        val onFailure: ((Throwable) -> Unit)?,
    ) : ActorRequest()

    data class Call(
        val objectId: Int,
        val methodVarIndex: Int,
        val args: List<ActorValue>,
        val response: Promise<ActorResponse>,
    ) : ActorRequest()

    /**
     * Invoke a function value owned by this actor — the delivery half of a
     * transferred callback, posted by a foreign `Call` opcode (Velo side) or
     * a host's `VeloFunction` (native side). [response] is `null` for
     * fire-and-forget posts; failures of those are program-fatal via
     * [ActorRuntime.raiseFatal] because nobody is waiting to observe them.
     */
    data class InvokeFunc(
        val func: vm.records.FuncRecord,
        val args: List<ActorValue>,
        val response: Promise<ActorResponse>?,
    ) : ActorRequest()

    /**
     * Re-enter a fiber that previously parked on an `await` (VEL-11). Posted by
     * the actor to its own dispatcher once the awaited future completes; [run]
     * restores the saved call stack and re-drives the interpreter. Carried as an
     * opaque continuation so this message stays decoupled from frame internals.
     */
    class Resume(val run: () -> Unit) : ActorRequest()

    object Shutdown : ActorRequest()
}

/**
 * Reply to an [ActorRequest], delivered via the request's [Promise].
 *
 * For [Call] (and callback invocations) it carries the method's return value
 * (already marshalled into [ActorValue]) — or [Failure] with a host-side
 * message describing the runtime issue that prevented the call.
 */
sealed class ActorResponse {
    data class Returned(val value: ActorValue) : ActorResponse()
    data class Failure(val message: String) : ActorResponse()
}
