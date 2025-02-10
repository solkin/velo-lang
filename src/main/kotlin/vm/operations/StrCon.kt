package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class StrCon : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val str1 = subs.pop().getString()
        val str2 = subs.pop().getString()

        val rec = ValueRecord(str2 + str1)

        subs.push(rec)
    }

}