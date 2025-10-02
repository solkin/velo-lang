package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Not: SimpleOperation {

    override fun exec(frame: Frame) {
        val v = frame.subs.pop().getInt()

        val result = ValueRecord(v.inv())

        frame.subs.push(result)
    }

}