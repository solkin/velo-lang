package vm.operations

import vm.Operation
import vm.VMContext
import vm.actors.ActorRefRecord
import vm.actors.FutureRecord
import vm.actors.popAndEncodeArgs

/**
 * Asynchronous cross-actor method invocation, emitted by `async receiver.method(args)`.
 *
 * Stack layout (top to bottom) on entry:
 *   1. arg_n, ..., arg_1     — method arguments in reverse push order
 *   2. receiver               — must be an [ActorRefRecord]
 *
 * Behaviour:
 *   - Pops args + receiver, structurally clones args via [StructuredClone],
 *     and submits an [vm.actors.ActorRequest.Call] to the receiver's actor.
 *   - **Does not block.** The submission returns a [java.util.concurrent.CompletableFuture]
 *     that the actor's worker thread will complete with the method result.
 *   - Wraps the future in a [FutureRecord] (which pins the actor alive for
 *     as long as the future is reachable) and pushes that onto the stack.
 *
 * The eventual value is unwrapped by [FutureAwait], emitted from the
 * matching `await` keyword on the future. `await async x.method()` therefore
 * compiles to exactly two opcodes: this one followed by `FutureAwait`.
 */
class ActorCall(
    val methodVarIndex: Int,
    val args: Int,
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        val cloned = popAndEncodeArgs(frame, args, ctx)
        val receiver = frame.subs.pop()
        require(receiver is ActorRefRecord) {
            "ActorCall expected actor[T] receiver, got ${receiver::class.simpleName}"
        }
        val future = receiver.handle.requestCallAsync(receiver.objectId, methodVarIndex, cloned)
        frame.subs.push(FutureRecord(receiver.handle, future))
        return pc + 1
    }
}
