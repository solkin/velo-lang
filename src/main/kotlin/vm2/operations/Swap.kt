package vm2.operations

import vm2.*
import vm2.Stack

class Swap: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()
        dataStack.push(rec1)
        dataStack.push(rec2)
    }

}