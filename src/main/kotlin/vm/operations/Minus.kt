package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Minus: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec1 = frame.subs.pop().getInt()
        val rec2 = frame.subs.pop().getInt()

        val result = ValueRecord(rec2 - rec1)
        
        frame.subs.push(result)
    }

}