package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord
import vm.records.ValueRecord

class DictArr : SimpleOperation {

    override fun exec(frame: Frame) {
        val dict = frame.subs.pop().getDict()

        val array = dict.toList().map { ValueRecord(it) }.toTypedArray()

        val rec = LinkRecord.create(array)
        frame.subs.push(rec)
    }

}