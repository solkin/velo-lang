package vm.operations

import vm.Operation
import vm.VMContext
import vm.actors.ActorRefRecord
import vm.actors.StructuredClone

/**
 * Cross-actor synchronous method invocation, emitted by `await receiver.method(args)`.
 *
 * Stack layout (top to bottom) on entry:
 *   1. arg_n, ..., arg_1     — method arguments in reverse push order
 *   2. receiver               — must be an [ActorRefRecord]
 *
 * Behaviour:
 *   - Pops args + receiver, structurally clones args, and submits an
 *     [vm.actors.ActorRequest.Call] to the receiver's [vm.actors.ActorHandle].
 *   - Blocks the caller thread until the actor responds. Since the actor
 *     runs on its own daemon thread, the caller's stack is preserved across
 *     the wait (no fiber/coroutine scheduling is involved).
 *   - Decodes the response back into a [vm.Record] in the caller's
 *     [vm.MemoryArea] and pushes it. For methods returning `actor[T]`, the
 *     response is an [ActorRefRecord]; for serialisable returns it's a fresh
 *     [vm.records.RefRecord] / [vm.records.ValueRecord].
 *
 * The compile-time `await` keyword is what ensures this opcode is only
 * emitted when crossing an actor boundary; calling a non-actor method via
 * this opcode would still throw at runtime via the receiver type check.
 */
class ActorAwaitCall(
    val methodVarIndex: Int,
    val args: Int,
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        val popped = Array(args) { frame.subs.pop() }
        val cloned = popped.reversed().map { StructuredClone.encode(it, ctx) }
        val receiver = frame.subs.pop()
        require(receiver is ActorRefRecord) {
            "ActorAwaitCall expected actor[T] receiver, got ${receiver::class.simpleName}"
        }
        val response = receiver.handle.requestCall(receiver.objectId, methodVarIndex, cloned)
        val result = StructuredClone.decode(response, ctx)
        frame.subs.push(result)
        return pc + 1
    }
}
