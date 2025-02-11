package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class StrCon : SimpleOperation {

    override fun exec(frame: Frame) {
        val str1 = frame.subs.pop().getString()
        val str2 = frame.subs.pop().getString()

        val rec = ValueRecord(str2 + str1)

        frame.subs.push(rec)
    }

}