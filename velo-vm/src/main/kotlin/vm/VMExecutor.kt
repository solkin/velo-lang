package vm

/**
 * Outcome of one [VMExecutor.run] turn: either the call stack ran to the end,
 * or a fiber parked on an `await` whose future was not ready (VEL-11) and its
 * frames remain on the stack for the driver to lift off and restore on resume.
 */
enum class RunResult { COMPLETED, SUSPENDED }

/**
 * Single-threaded interpreter loop, decoupled from [VM].
 *
 * `VM` uses it to run the program's main frame; [vm.actors.ActorHandle]
 * reuses the exact same loop on each actor's worker thread. Sharing the
 * loop guarantees that operations behave identically inside and outside
 * actors (same frame/stack semantics, same profiling hook).
 *
 * The loop runs the operations of [VMContext.currentFrame] until that
 * frame's program counter reaches the end, then walks back into the
 * caller frame (if any) on the call stack and continues. When all frames
 * are popped, the loop exits.
 *
 * Actor request handling pushes a *sentinel* frame with zero ops as a
 * stop marker before invoking a method or constructor. As soon as the
 * sentinel becomes current, the loop exits naturally and the caller can
 * inspect the sentinel's operand stack for the return value.
 */
class VMExecutor(
    private val ctx: VMContext,
    private val profiler: VMProfiler? = null,
) {

    /**
     * Drive the interpreter forward until either the call stack is
     * empty or the current frame has finished executing all its ops.
     *
     * Throws whatever the underlying operations throw — the caller is
     * responsible for rewinding the stack/state on failure.
     */
    fun run(): RunResult {
        if (ctx.isStackEmpty()) return RunResult.COMPLETED
        var frame = ctx.currentFrame()
        while (frame.pc < frame.ops.size) {
            val cmd = frame.ops[frame.pc]
            profiler?.beforeOp(cmd)
            frame.pc = Interpreter.exec(cmd, pc = frame.pc, ctx)
            profiler?.afterOp()
            // VEL-11: an `await` on a not-yet-ready future parks the fiber here
            // instead of blocking the thread. The frames are left on the stack
            // for the driver to lift off and restore when the future completes.
            if (ctx.hasPendingSuspend()) return RunResult.SUSPENDED
            if (ctx.isStackEmpty()) return RunResult.COMPLETED
            frame = ctx.currentFrame()
        }
        return RunResult.COMPLETED
    }
}
