package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class ArrOf : SimpleOperation {

    override fun exec(frame: Frame) {
        val size = frame.subs.pop().getInt()
        val array = Array(size, { i ->
            frame.subs.pop()
        }).apply { reverse() }
        val rec = ValueRecord(array)
        frame.subs.push(rec)
    }

}