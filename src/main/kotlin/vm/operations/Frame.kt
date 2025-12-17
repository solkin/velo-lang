package vm.operations

import vm.Operation
import vm.VMContext
import vm.records.FrameRecord

class Frame(
    val num: Int
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val rec = FrameRecord(num)
        ctx.currentFrame().subs.push(rec)
        return pc + 1
    }

}