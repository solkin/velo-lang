package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Equals: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val val1 = dataStack.pop().getInt()
        val val2 = dataStack.pop().getInt()

        val result = ValueRecord(val1 == val2)

        dataStack.push(result)
    }

}