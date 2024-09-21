package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class And: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val val1 = dataStack.pop().getBool()
        val val2 = dataStack.pop().getBool()

        val result = ValueRecord(val1.and(val2))

        dataStack.push(result)
    }

}