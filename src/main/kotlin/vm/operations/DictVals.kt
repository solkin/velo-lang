package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class DictVals : SimpleOperation {

    override fun exec(frame: Frame) {
        val dict = frame.subs.pop().getDict()

        val array = dict.values.toTypedArray()

        val rec = LinkRecord.create(array)
        frame.subs.push(rec)
    }

}