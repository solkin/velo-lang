package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord
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