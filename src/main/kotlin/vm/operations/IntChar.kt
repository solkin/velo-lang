package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class IntChar : SimpleOperation {

    override fun exec(frame: Frame) {
        val v = frame.subs.pop().getInt()

        val rec = ValueRecord(Char(v).toString())

        frame.subs.push(rec)
    }

}