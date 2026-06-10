package vm.operations

import vm.Operation
import vm.VMContext
import vm.actors.ActorHandle
import vm.actors.ActorRefRecord
import vm.actors.popAndEncodeArgs

/**
 * Instantiate an `actor class` on a fresh worker thread.
 *
 * Stack layout (top to bottom) on entry:
 *   1. arg_n, ..., arg_1   — constructor arguments in reverse push order
 *
 * Behaviour:
 *   - Pops [args] arguments off the operand stack and structurally clones
 *     them (see [StructuredClone]) so the new actor doesn't alias caller
 *     state.
 *   - Spawns a new [ActorHandle] sharing the program's [vm.FrameLoader] and
 *     [vm.NativeRegistry], runs the constructor frame numbered
 *     [classFrameNum] on the worker, and waits for completion.
 *   - Pushes back an [ActorRefRecord] pointing at the freshly created root
 *     object. This record is what the caller's `actor[T]` variable holds.
 *
 * Why not reuse `Frame` + `Call` + `Instance`? Because every step here lives
 * on a different thread from the caller. A single opcode keeps the
 * cross-thread handshake (queue push / future await / object-id assignment)
 * atomic from the bytecode's perspective.
 */
class ActorSpawn(
    val classFrameNum: Int,
    val className: String,
    val args: Int,
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        val cloned = popAndEncodeArgs(frame, args, ctx)

        val (handle, rootObjectId) = ActorHandle.spawn(
            runtime = ctx.actorRuntime,
            sharedFrameLoader = ctx.frameLoader,
            sharedNativeRegistry = ctx.nativeRegistry,
            sharedNatives = ctx.natives,
            classFrameNum = classFrameNum,
            className = className,
            args = cloned,
        )
        frame.subs.push(ActorRefRecord(handle, rootObjectId, className))
        return pc + 1
    }
}
