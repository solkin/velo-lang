package vm.operations

import vm.Operation
import vm.VMContext
import vm.Vars
import vm.records.FuncRecord
import kotlin.math.abs

/**
 * Invokes a callable value sitting on top of the operand stack.
 *
 * Stack layout (top to bottom) at the moment of execution:
 * 1. callable             — a [FuncRecord] (or, for legacy bytecode, a
 *                           plain value containing the frame number)
 * 2. arg_n, arg_{n-1}, ... — actual arguments, count `|args|`
 * 3. receiver frame        — present iff [classParent] is `true`,
 *                           popped as the parent for class-method dispatch
 *
 * Closure / scope rules:
 * - When the callable is a [FuncRecord] and [classParent] is `false`,
 *   the new frame's parent vars are the function's
 *   [FuncRecord.capturedVars] — i.e., the lexical (definition-site)
 *   scope. This makes escaping closures behave correctly.
 * - When [classParent] is `true`, the receiver instance's vars override
 *   the callable's captured chain — required for `instance.method(...)`
 *   dispatch.
 * - Legacy fallback: for older bytecode the callable may be a plain
 *   integer record. In that case we fall back to the caller's vars,
 *   which preserves pre-FuncRecord behaviour.
 */
class Call(val args: Int, val classParent: Boolean = false) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val thisFrame = ctx.currentFrame()
        val callable = thisFrame.subs.pop()
        val frameNum: Int
        val capturedVars: Vars?
        when (callable) {
            is FuncRecord -> {
                frameNum = callable.frameNum
                capturedVars = callable.capturedVars
            }
            else -> {
                frameNum = callable.getInt()
                capturedVars = null
            }
        }

        val argsArray = Array(size = abs(args), init = { thisFrame.subs.pop() })
            .let { arr -> if (args > 0) arr.reversedArray() else arr }

        val parentVars: Vars? = if (classParent) {
            thisFrame.subs.pop().getFrame().vars
        } else {
            capturedVars ?: thisFrame.vars
        }

        val newFrame = ctx.loadFrame(num = frameNum, parentVars = parentVars)
            ?: throw Exception("Frame $frameNum not found")
        argsArray.forEach { arg -> newFrame.subs.push(arg) }
        ctx.pushFrame(newFrame)
        return pc + 1
    }

}
