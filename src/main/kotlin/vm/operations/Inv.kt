package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Inv: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val v = frame.subs.pop().getInt()

        val result = ValueRecord(v.inv())

        frame.subs.push(result)
    }

}