package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class LessEquals: SimpleOperation {

    override fun exec(frame: Frame) {
        val val1 = frame.subs.pop().getInt()
        val val2 = frame.subs.pop().getInt()

        val result = ValueRecord(val2 <= val1)

        frame.subs.push(result)
    }

}