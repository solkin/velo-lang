package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.minus
import vm.records.ValueRecord

class Minus: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec1 = frame.subs.pop().getNumber()
        val rec2 = frame.subs.pop().getNumber()

        val result = ValueRecord(rec2.minus(rec1))
        
        frame.subs.push(result)
    }

}