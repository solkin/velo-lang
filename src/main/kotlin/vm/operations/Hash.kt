package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class Hash: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val rec = frame.subs.pop()

        val result = ValueRecord(value = rec.hashCode())

        frame.subs.push(value = result)
    }

}