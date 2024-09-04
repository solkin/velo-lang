package vm2.operations

import vm2.*
import vm2.Stack

class Print : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val value = dataStack.pop().get()
        print(value.toString())
    }

}