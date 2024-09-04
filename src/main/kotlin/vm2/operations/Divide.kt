package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class Divide: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop().getInt()
        val rec2 = dataStack.pop().getInt()

        val result = ValueRecord(rec2 / rec1)

        dataStack.push(result)
    }

}