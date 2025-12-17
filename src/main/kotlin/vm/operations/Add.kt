package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.plus
import vm.records.ValueRecord

class Add: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val rec1 = frame.subs.pop().getNumber()
        val rec2 = frame.subs.pop().getNumber()

        val result = ValueRecord(rec2.plus(rec1))

        frame.subs.push(result)
    }

}