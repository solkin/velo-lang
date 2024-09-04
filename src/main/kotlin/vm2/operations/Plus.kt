package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class Plus: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()

        val result = ValueRecord(rec1.getInt() + rec2.getInt())

        dataStack.push(result)
    }

}