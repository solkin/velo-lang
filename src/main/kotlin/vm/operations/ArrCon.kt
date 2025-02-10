package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrCon : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val arr1 = subs.pop().getArray()
        val arr2 = subs.pop().getArray()

        val rec = ValueRecord(arr2.plus(arr1))

        subs.push(rec)
    }

}
