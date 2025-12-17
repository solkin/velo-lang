package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class StrInt : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val str = frame.subs.pop().getString()

        val rec = ValueRecord(str.toInt())

        frame.subs.push(rec)
    }

}