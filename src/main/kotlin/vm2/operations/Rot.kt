package vm2.operations

import vm2.*
import vm2.Stack

class Rot: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()
        val rec3 = dataStack.pop()
        dataStack.push(rec2)
        dataStack.push(rec1)
        dataStack.push(rec3)
    }

}