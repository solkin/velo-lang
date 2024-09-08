package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class LessEquals: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val val1 = dataStack.pop().getInt()
        val val2 = dataStack.pop().getInt()

        val result = ValueRecord(val2 <= val1)

        dataStack.push(result)
    }

}