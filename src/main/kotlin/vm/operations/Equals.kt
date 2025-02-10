package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Equals: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val val1 = subs.pop()
        val val2 = subs.pop()

        val result = ValueRecord(val1 == val2)

        subs.push(result)
    }

}