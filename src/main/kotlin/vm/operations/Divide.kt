package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.divide
import vm.records.ValueRecord

class Divide: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec1 = frame.subs.pop().getNumber()
        val rec2 = frame.subs.pop().getNumber()

        val result = ValueRecord(rec2.divide(rec1))

        frame.subs.push(result)
    }

}