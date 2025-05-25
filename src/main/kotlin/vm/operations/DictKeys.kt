package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class DictKeys : SimpleOperation {

    override fun exec(frame: Frame) {
        val dict = frame.subs.pop().getDict()

        val array = dict.keys.toTypedArray()

        val rec = LinkRecord.create(array)
        frame.subs.push(rec)
    }

}