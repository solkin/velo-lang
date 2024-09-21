package vm.operations

import vm.Activation
import vm.Heap
import vm.OperationNotSupportedException
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Pick: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        throw OperationNotSupportedException()
    }

}