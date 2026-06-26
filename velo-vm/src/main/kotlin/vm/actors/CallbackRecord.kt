package vm.actors

import vm.Record
import vm.records.FuncRecord

/**
 * A function value owned by *another* actor — the receiving side of a
 * transferred `func[(args) void]`.
 *
 * The closure itself ([func] with its captured `Vars` chain) never leaves
 * the owner's memory; this record is a typed remote trigger. Invoking it
 * (via the `Call` opcode) encodes the arguments and posts an
 * [ActorRequest.InvokeFunc] to the owner's dispatcher, so the body always
 * executes on the thread that owns the captured state. The invocation is
 * fire-and-forget: callbacks crossing a boundary are void by construction
 * (enforced by the compiler's transferability rules), which keeps
 * `A await B → B calls back into A` free of deadlocks.
 *
 * Lifetime: pins nothing — actors live until explicit shutdown or program exit.
 * (Keeping the event loop alive for a callback that fires from *outside* the VM
 * is the host's job via [core.VeloFunction.retain]/release, not this
 * Velo-internal handle.)
 */
class CallbackRecord(
    val handle: ActorHandle,
    val func: FuncRecord,
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = this as T

    override fun toString(): String = "callback@actor${handle.id}:frame${func.frameNum}"
}
