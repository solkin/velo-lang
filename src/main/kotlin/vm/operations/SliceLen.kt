package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class SliceLen : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val slice = dataStack.pop().getSlice()

        val rec = ValueRecord(slice.size)

        dataStack.push(rec)
    }

}