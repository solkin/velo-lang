package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class ArrLen : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val array = frame.subs.pop().getArray()

        val rec = ValueRecord(array.size)

        frame.subs.push(rec)
    }

}
