package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class And: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val val1 = subs.pop().getBool()
        val val2 = subs.pop().getBool()

        val result = ValueRecord(val1.and(val2))

        subs.push(result)
    }

}