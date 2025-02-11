package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Equals: SimpleOperation {

    override fun exec(frame: Frame) {
        val val1 = frame.subs.pop()
        val val2 = frame.subs.pop()

        val result = ValueRecord(val1 == val2)

        frame.subs.push(result)
    }

}