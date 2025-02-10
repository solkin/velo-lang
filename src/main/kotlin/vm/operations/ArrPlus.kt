package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrPlus : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val v = subs.pop()
        val arr = subs.pop().getArray()

        val rec = ValueRecord(arr.plus(v))

        subs.push(rec)
    }

}
