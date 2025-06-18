package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Shl: SimpleOperation {

    override fun exec(frame: Frame) {
        val bits = frame.subs.pop().getInt()
        val value = frame.subs.pop().getInt()

        val result = ValueRecord(value.shl(bits))
        
        frame.subs.push(result)
    }

}