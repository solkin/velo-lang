package vm

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
    fun run() {
        if (ctx.isStackEmpty()) return
        var frame = ctx.currentFrame()
        while (frame.pc < frame.ops.size) {
            val cmd = frame.ops[frame.pc]
            profiler?.beforeOp(cmd)
            frame.pc = Interpreter.exec(cmd, pc = frame.pc, ctx)
            profiler?.afterOp()
            if (ctx.isStackEmpty()) return
            frame = ctx.currentFrame()
        }
    }
}
