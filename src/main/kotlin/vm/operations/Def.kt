package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Def(
    val index: Int,
): SimpleOperation {

    override fun exec(frame: Frame) {
        val value = frame.subs.pop().get()
        frame.vars.def(index, ValueRecord(value))
    }

}