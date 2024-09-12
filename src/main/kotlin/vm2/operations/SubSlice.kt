package vm2.operations

import vm2.*
import vm2.Stack
import vm2.records.ValueRecord
import java.util.*

class SubSlice : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val start = dataStack.pop().getInt()
        val end = dataStack.pop().getInt()
        val slice = dataStack.pop().getSlice()

        val rec = ValueRecord(Arrays.copyOfRange(slice, start, end))

        dataStack.push(rec)
    }

}