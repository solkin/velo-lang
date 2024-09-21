package vm.operations

import vm.Activation
import vm.HaltException
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Halt: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        throw HaltException()
    }

}