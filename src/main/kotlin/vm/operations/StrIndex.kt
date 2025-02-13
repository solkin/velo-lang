package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class StrIndex : SimpleOperation {

    override fun exec(frame: Frame) {
        val index = frame.subs.pop().getInt()
        val str = frame.subs.pop().getString()

        val rec = ValueRecord(str[index].code)

        frame.subs.push(rec)
    }

}