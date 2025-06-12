package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.plus
import vm.records.ValueRecord

class Plus: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec1 = frame.subs.pop().getNumber()
        val rec2 = frame.subs.pop().getNumber()

        val result = ValueRecord(rec2.plus(rec1))

        frame.subs.push(result)
    }

}