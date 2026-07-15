package vm

import vm.records.ValueRecord

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
            profiler?.countOp()
            profiler?.beforeOp(cmd)
            try {
                frame.pc = Interpreter.exec(cmd, pc = frame.pc, ctx)
            } catch (halt: HaltException) {
                throw halt // a user `halt` is never catchable (nor is a sandbox limit)
            } catch (ex: Throwable) {
                // VEL-9: route a catchable failure to the nearest `try` handler.
                // On a miss the call stack is left intact and the failure
                // propagates, preserving the diagnostic dump and pre-VEL-9
                // "every error is fatal" semantics.
                if (!unwindToHandler(ex)) throw ex
            }
            profiler?.afterOp()
            // Reclaim unreachable heap slots between ops, where the operand
            // stacks hold every live value (so roots are precise). Cheap when
            // the heap is below threshold — a field compare.
            ctx.collectIfNeeded()
            // VEL-11: an `await` on a not-yet-ready future parks the fiber here
            // instead of blocking the thread. The frames are left on the stack
            // for the driver to lift off and restore when the future completes.
            if (ctx.hasPendingSuspend()) return RunResult.SUSPENDED
            if (ctx.isStackEmpty()) return RunResult.COMPLETED
            frame = ctx.currentFrame()
        }
        return RunResult.COMPLETED
    }

    /**
     * Route a raised failure (a Velo `throw` or a JVM exception from a failing
     * op) to the nearest active `try` handler. Returns false — stack untouched —
     * when no frame holds one, so the caller rethrows and the runtime's error
     * dump still sees the full stack. Otherwise unwinds: drops the frames above
     * the handler's, restores that frame's scope and operand depth (undoing any
     * open scope and half-evaluated operands), pushes the Error value and jumps
     * to the catch block. Handlers live on frames, so a fiber parked on an
     * `await` inside a `try` keeps its handler across the suspension for free.
     */
    private fun unwindToHandler(ex: Throwable): Boolean {
        var hasHandler = false
        ctx.stack.forEach { if (it.handlers?.isNotEmpty() == true) hasHandler = true }
        if (!hasHandler) return false

        val error = errorRecord(ex)
        while (!ctx.isStackEmpty()) {
            val frame = ctx.currentFrame()
            val handler = frame.handlers?.takeIf { it.isNotEmpty() }?.removeLast()
            if (handler != null) {
                frame.vars = handler.savedVars
                while (frame.subs.size() > handler.subsDepth) frame.subs.pop()
                frame.subs.push(error)
                frame.pc = handler.catchPc
                return true
            }
            ctx.popFrame()
        }
        return false // unreachable: the pre-scan guaranteed a handler exists
    }

    /**
     * The Velo `Error` value for a raised failure. A user `throw` already carries
     * one ([VeloThrow]); a runtime/native/actor failure is rebuilt into an
     * `Error(kind, message)` from the program's stdlib Error frame — the same
     * re-run-the-constructor path actor/native marshalling uses.
     */
    private fun errorRecord(ex: Throwable): Record {
        if (ex is VeloThrow) return ex.error
        val frameNum = ctx.errorClassFrameNum
            ?: throw IllegalStateException("cannot build an Error: std/error is not in the program", ex)
        val fields = listOf<Record>(ValueRecord(errorKind(ex)), ValueRecord(ex.message ?: ex.toString()))
        return reconstructData(frameNum, fields, ctx)
    }

    /** Classify a JVM failure into an `ERR_*` kind (see std/error.vel). */
    private fun errorKind(ex: Throwable): String = when {
        ex is ArithmeticException -> "arithmetic"
        ex is IndexOutOfBoundsException -> "bounds"
        ex is NullPointerException -> "null"
        ex.message?.startsWith("Native call ") == true -> "native"
        ex.message?.startsWith("[actor ") == true -> "actor"
        else -> "generic"
    }
}
