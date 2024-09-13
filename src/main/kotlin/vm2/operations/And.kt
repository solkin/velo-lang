package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Record
import vm2.SimpleOperation
import vm2.Stack
import vm2.records.ValueRecord

class And: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val val1 = dataStack.pop().getBool()
        val val2 = dataStack.pop().getBool()

        val result = ValueRecord(val1.and(val2))

        dataStack.push(result)
    }

}