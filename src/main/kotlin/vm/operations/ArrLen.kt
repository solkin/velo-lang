package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class ArrLen : SimpleOperation {

    override fun exec(frame: Frame) {
        val array = frame.subs.pop().getArray()

        val rec = ValueRecord(array.size)

        frame.subs.push(rec)
    }

}