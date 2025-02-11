package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Multiply: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec1 = frame.subs.pop().getInt()
        val rec2 = frame.subs.pop().getInt()

        val result = ValueRecord(rec1 * rec2)

        frame.subs.push(result)
    }

}