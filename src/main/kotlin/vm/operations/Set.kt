package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Set(
    val index: Int,
): SimpleOperation {

    override fun exec(frame: Frame) {
        val value = frame.subs.pop().get()
        frame.set(index, ValueRecord(value))
    }

}