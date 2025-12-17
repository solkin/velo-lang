package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class StrLen : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val str = frame.subs.pop().getString()

        val rec = ValueRecord(str.length)

        frame.subs.push(rec)
    }

}