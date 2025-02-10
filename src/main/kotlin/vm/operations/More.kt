package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class More: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val val1 = subs.pop().getInt()
        val val2 = subs.pop().getInt()

        val result = ValueRecord(val2 > val1)

        subs.push(result)
    }

}