package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Push(
    val value: Any
): SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = ValueRecord(value)
        frame.subs.push(rec)
    }

}