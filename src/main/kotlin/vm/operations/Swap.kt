package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Swap: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()
        dataStack.push(rec1)
        dataStack.push(rec2)
    }

}