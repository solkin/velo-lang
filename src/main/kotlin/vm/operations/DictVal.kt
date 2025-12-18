package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class DictVal : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val value = frame.subs.pop()
        val dict = frame.subs.pop().getDict()

        val rec = ValueRecord(dict.containsValue(value))

        frame.subs.push(rec)
    }

}
