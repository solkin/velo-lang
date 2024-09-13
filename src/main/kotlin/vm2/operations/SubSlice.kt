package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Record
import vm2.SimpleOperation
import vm2.Stack
import vm2.records.ValueRecord
import java.util.Arrays

class SubSlice : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val start = dataStack.pop().getInt()
        val end = dataStack.pop().getInt()
        val slice = dataStack.pop().getSlice()

        val rec = ValueRecord(Arrays.copyOfRange(slice, start, end))

        dataStack.push(rec)
    }

}