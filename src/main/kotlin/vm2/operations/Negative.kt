package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class Negative: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec = dataStack.pop().getInt()

        val result = ValueRecord(-rec)

        dataStack.push(result)
    }

}