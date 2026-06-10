package vm.actors

import vm.Frame
import vm.VMContext

/**
 * Pop [count] arguments off [frame]'s operand stack and structurally clone
 * them into [ActorValue]s ready to ship across an actor boundary.
 *
 * The stack pops in reverse-of-push order, so the returned list is
 * re-ordered to match the original declaration order — exactly what an
 * actor's worker thread expects when it pushes them back onto the target
 * frame inside [ActorHandle].
 *
 * Allocates one fixed-size array (then exposes it as a `List`) instead of
 * the `Array().reversed().map { ... }` chain used by the first cut, which
 * matters when the same opcode runs in tight loops (e.g. fan-out via
 * `async` inside another actor).
 */
internal fun popAndEncodeArgs(frame: Frame, count: Int, ctx: VMContext): List<ActorValue> {
    if (count == 0) return emptyList()
    val cloned = arrayOfNulls<ActorValue>(count)
    for (i in count - 1 downTo 0) {
        cloned[i] = StructuredClone.encode(frame.subs.pop(), ctx)
    }
    @Suppress("UNCHECKED_CAST")
    return (cloned as Array<ActorValue>).asList()
}
