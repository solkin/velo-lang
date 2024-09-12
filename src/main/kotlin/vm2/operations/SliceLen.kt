package vm2.operations

import vm2.*
import vm2.Stack
import vm2.records.ValueRecord

class SliceLen : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val slice = dataStack.pop().getSlice()

        val rec = ValueRecord(slice.size)

        dataStack.push(rec)
    }

}