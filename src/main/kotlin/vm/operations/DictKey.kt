package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class DictKey : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val key = frame.subs.pop()
        val dict = frame.subs.pop().getDict()

        val rec = ValueRecord(dict.containsKey(key))

        frame.subs.push(rec)
    }

}