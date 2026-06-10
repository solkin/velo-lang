package vm.operations

import vm.Operation
import vm.VMContext
import vm.records.RefRecord

/**
 * Materialise the currently executing class frame as a class-instance
 * record — the value `new X(...)` leaves on the operand stack.
 */
class Instance : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        val record = RefRecord.classInstance(frame, nativeIndex = null, ctx)
        frame.subs.push(value = record)
        return pc + 1
    }

}
