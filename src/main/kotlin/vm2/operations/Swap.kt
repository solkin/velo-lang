package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Record
import vm2.SimpleOperation
import vm2.Stack

class Swap: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()
        dataStack.push(rec1)
        dataStack.push(rec2)
    }

}