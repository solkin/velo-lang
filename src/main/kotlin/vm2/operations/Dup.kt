package vm2.operations

import vm2.*
import vm2.Stack

class Dup: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec = dataStack.peek()
        dataStack.push(rec)
    }

}