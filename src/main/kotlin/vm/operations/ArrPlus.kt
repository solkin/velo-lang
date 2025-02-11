package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class ArrPlus : SimpleOperation {

    override fun exec(frame: Frame) {
        val v = frame.subs.pop()
        val arr = frame.subs.pop().getArray()

        val rec = ValueRecord(arr.plus(v))

        frame.subs.push(rec)
    }

}
