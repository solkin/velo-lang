package vm2.operations

import vm2.*
import vm2.Stack

class Drop: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        dataStack.pop()
    }

}