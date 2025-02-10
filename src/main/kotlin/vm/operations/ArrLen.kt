package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrLen : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val array = subs.pop().getArray()

        val rec = ValueRecord(array.size)

        subs.push(rec)
    }

}