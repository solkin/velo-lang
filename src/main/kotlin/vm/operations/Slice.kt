package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Slice : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val size = dataStack.pop().getInt()
        val slice = Array(size, { i ->
            dataStack.pop()
        }).apply { reverse() }
        val rec = ValueRecord(slice)
        dataStack.push(rec)
    }

}