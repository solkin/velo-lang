package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class DictIndex : SimpleOperation {

    override fun exec(frame: Frame) {
        val key = frame.subs.pop()
        val dict = frame.subs.pop().getDict()

        val rec = dict[key] ?: ValueRecord(Unit)

        frame.subs.push(rec)
    }

}