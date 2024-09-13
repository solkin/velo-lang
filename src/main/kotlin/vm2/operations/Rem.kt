package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Record
import vm2.SimpleOperation
import vm2.Stack
import vm2.records.ValueRecord

class Rem: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop().getInt()
        val rec2 = dataStack.pop().getInt()

        val result = ValueRecord(rec2 % rec1)

        dataStack.push(result)
    }

}