package vm.operations

import vm.Operation
import vm.VMContext
import vm.records.FuncRecord

/**
 * Pushes a function value onto the operand stack.
 *
 * The function value is materialised as a [FuncRecord] that captures
 * the current frame's [vm.Vars] chain at creation time. This gives the
 * function lexical (definition-site) scoping: when invoked later — even
 * after the defining frame has returned — captured outer variables
 * remain reachable through [FuncRecord.capturedVars].
 */
class Frame(
    val num: Int
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val current = ctx.currentFrame()
        current.subs.push(FuncRecord(frameNum = num, capturedVars = current.vars))
        return pc + 1
    }

}
