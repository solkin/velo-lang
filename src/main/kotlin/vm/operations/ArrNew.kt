package vm.operations

import vm.VMContext

import vm.Frame
import vm.Record
import vm.SimpleOperation
import vm.records.EmptyRecord
import vm.records.RefRecord

class ArrNew : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val size = frame.subs.pop().getInt()

        val array = Array<Record>(size) { EmptyRecord }
        val rec = RefRecord.array(array, ctx)

        frame.subs.push(value = rec)
    }

}
