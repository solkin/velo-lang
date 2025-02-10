package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Plus: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec1 = subs.pop().getInt()
        val rec2 = subs.pop().getInt()

        val result = ValueRecord(rec1 + rec2)

        subs.push(result)
    }

}