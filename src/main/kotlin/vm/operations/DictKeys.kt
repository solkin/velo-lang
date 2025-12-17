package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class DictKeys : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val dict = frame.subs.pop().getDict()

        val array = dict.keys.toTypedArray()

        val rec = LinkRecord.create(array, ctx)
        frame.subs.push(rec)
    }

}