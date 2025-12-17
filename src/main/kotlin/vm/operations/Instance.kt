package vm.operations

import vm.Operation
import vm.VMContext
import vm.records.ClassRecord

class Instance(val nativeIndex: Int?) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        val record = ClassRecord.create(frame, nativeIndex, ctx)
        frame.subs.push(value = record)
        return pc + 1
    }

}