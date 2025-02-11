package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class IntStr : SimpleOperation {

    override fun exec(frame: Frame) {
        val v = frame.subs.pop().getInt()

        val rec = ValueRecord(v.toString())

        frame.subs.push(rec)
    }

}