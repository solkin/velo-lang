package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Shr: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val bits = frame.subs.pop().getInt()
        val value = frame.subs.pop().getInt()

        val result = ValueRecord(value.shr(bits))
        
        frame.subs.push(result)
    }

}