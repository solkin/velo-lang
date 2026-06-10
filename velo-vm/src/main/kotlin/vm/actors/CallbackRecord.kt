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
 * Lifetime: pins the owning actor via [ActorHandle.refCount] + [Pins], the
 * same model as [ActorRefRecord] — "someone may still call me" keeps the
 * owner's dispatcher serviceable. For the main context this is exactly what
 * keeps the program's pump loop alive after the main frame completes.
 */
class CallbackRecord(
    val handle: ActorHandle,
    val func: FuncRecord,
) : Record {

    init {
        handle.refCount.incrementAndGet()
        Pins.cleaner.register(this, Pins.Release(handle))
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = this as T

    override fun toString(): String = "callback@actor${handle.id}:frame${func.frameNum}"
}
