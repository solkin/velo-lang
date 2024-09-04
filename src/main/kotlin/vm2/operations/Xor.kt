package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import vm2.Stack

class Xor: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val val1 = dataStack.pop().getBool()
        val val2 = dataStack.pop().getBool()

        val result = ValueRecord(val1.xor(val2))

        dataStack.push(result)
    }

}