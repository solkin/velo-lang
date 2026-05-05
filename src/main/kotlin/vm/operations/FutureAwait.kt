package vm.operations

import vm.Operation
import vm.VMContext
import vm.actors.FutureRecord
import vm.actors.StructuredClone

/**
 * Block until a pending cross-actor call completes, emitted by
 * `await futureExpr`.
 *
 * Stack layout (top to bottom) on entry:
 *   1. future — must be a [FutureRecord]
 *
 * Behaviour:
 *   - Pops the [FutureRecord] and joins its underlying
 *     [java.util.concurrent.CompletableFuture] on the calling thread.
 *   - Decodes the actor's response via [StructuredClone.decode] into a
 *     fresh [vm.Record] in the caller's [vm.MemoryArea] and pushes it.
 *   - Failures inside the actor surface here as [RuntimeException]s carrying
 *     the actor-tagged message, so the user sees the same diagnostic shape
 *     whether they used `await async x.foo()` (one statement) or stored the
 *     future and awaited it later.
 *
 * `CompletableFuture.join()` is idempotent, so awaiting the same future
 * twice returns the same value without re-running the actor method.
 */
class FutureAwait : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        val rec = frame.subs.pop()
        require(rec is FutureRecord) {
            "FutureAwait expected future[T] value, got ${rec::class.simpleName}"
        }
        val response = rec.handle.unwrapResponse(rec.future.join())
        frame.subs.push(StructuredClone.decode(response, ctx))
        return pc + 1
    }
}
