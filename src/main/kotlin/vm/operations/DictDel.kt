package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class DictDel : SimpleOperation {

    override fun exec(frame: Frame) {
        val key = frame.subs.pop()
        val dict = frame.subs.pop().getDict()

        val rec = ValueRecord(dict.remove(key) != null)

        frame.subs.push(rec)
    }

}