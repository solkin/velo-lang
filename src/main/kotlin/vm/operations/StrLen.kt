package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class StrLen : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val str = subs.pop().getString()

        val rec = ValueRecord(str.length)

        subs.push(rec)
    }

}