package vm.operations

import core.Op
import vm.Frame
import vm.Interpreter
import vm.VMContext

/**
 * Test-only bridge preserving the `op.exec(frame, ctx)` entry point the
 * operation unit-tests were written against.
 *
 * Ops are inert data executed by [Interpreter], which works on the
 * context's current frame. These tests construct a standalone [frame] and
 * run a single op against it, so we make [frame] current for the duration
 * of the op and restore the stack afterwards. Only stack-local ops
 * (arithmetic, logical, stack, pointer) are exercised this way — none push
 * or pop call frames — so the surrounding stack is left exactly as it was.
 */
fun Op.exec(frame: Frame, ctx: VMContext): Int {
    ctx.pushFrame(frame)
    try {
        return Interpreter.exec(this, 0, ctx)
    } finally {
        ctx.popFrame()
    }
}
