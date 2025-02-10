package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class ArrOf : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val size = subs.pop().getInt()
        val array = Array(size, { i ->
            subs.pop()
        }).apply { reverse() }
        val rec = ValueRecord(array)
        subs.push(rec)
    }

}