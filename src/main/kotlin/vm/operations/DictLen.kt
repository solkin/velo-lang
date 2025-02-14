package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class DictLen : SimpleOperation {

    override fun exec(frame: Frame) {
        val dict = frame.subs.pop().getDict()

        val rec = ValueRecord(dict.size)

        frame.subs.push(rec)
    }

}