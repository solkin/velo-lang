package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Not: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.subs.pop()

        val result = ValueRecord(rec.getBool().not())

        frame.subs.push(result)
    }

}