package vm2.operations

import vm2.*
import vm2.Stack

class Halt: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        throw HaltException()
    }

}