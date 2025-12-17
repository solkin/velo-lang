package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class IntStr : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val v = frame.subs.pop().getInt()

        val rec = ValueRecord(v.toString())

        frame.subs.push(rec)
    }

}