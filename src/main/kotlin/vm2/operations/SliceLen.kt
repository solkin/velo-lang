package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Record
import vm2.SimpleOperation
import vm2.Stack
import vm2.records.ValueRecord

class SliceLen : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val slice = dataStack.pop().getSlice()

        val rec = ValueRecord(slice.size)

        dataStack.push(rec)
    }

}