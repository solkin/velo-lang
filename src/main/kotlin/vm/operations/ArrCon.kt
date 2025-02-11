package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class ArrCon : SimpleOperation {

    override fun exec(frame: Frame) {
        val arr1 = frame.subs.pop().getArray()
        val arr2 = frame.subs.pop().getArray()

        val rec = ValueRecord(arr2.plus(arr1))

        frame.subs.push(rec)
    }

}
