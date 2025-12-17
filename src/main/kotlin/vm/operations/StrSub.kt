package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class StrSub : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val start = frame.subs.pop().getInt()
        val end = frame.subs.pop().getInt()
        val str = frame.subs.pop().getString()

        val rec = ValueRecord(str.substring(start, end))

        frame.subs.push(rec)
    }

}