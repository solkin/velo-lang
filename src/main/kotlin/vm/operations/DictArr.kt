package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord
import vm.records.ValueRecord

class DictArr : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val dict = frame.subs.pop().getDict()

        val array = dict.toList().map { ValueRecord(arrayOf(it.first, it.second)) }.toTypedArray()

        val rec = LinkRecord.create(array, ctx)
        frame.subs.push(rec)
    }

}