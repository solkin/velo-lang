package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Negative: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.subs.pop().getInt()

        val result = ValueRecord(-rec)

        frame.subs.push(result)
    }

}