package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Or: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val val1 = frame.subs.pop().getInt()
        val val2 = frame.subs.pop().getInt()

        val result = ValueRecord(val1.or(val2))

        frame.subs.push(result)
    }

}