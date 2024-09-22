package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord
import java.util.Arrays

class SubArr : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val start = dataStack.pop().getInt()
        val end = dataStack.pop().getInt()
        val array = dataStack.pop().getArray()

        val rec = ValueRecord(Arrays.copyOfRange(array, start, end))

        dataStack.push(rec)
    }

}