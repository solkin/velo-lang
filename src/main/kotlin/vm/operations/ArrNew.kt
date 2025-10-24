package vm.operations

import vm.Frame
import vm.Record
import vm.SimpleOperation
import vm.records.EmptyRecord
import vm.records.ValueRecord

class ArrNew : SimpleOperation {

    override fun exec(frame: Frame) {
        val size = frame.subs.pop().getInt()

        val rec = ValueRecord(value = Array<Record>(size) { EmptyRecord })

        frame.subs.push(value = rec)
    }

}